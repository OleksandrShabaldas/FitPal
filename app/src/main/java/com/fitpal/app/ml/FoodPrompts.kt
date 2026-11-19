package com.fitpal.app.ml

import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.UserProfile

/**
 * The prompts the AI engines send, kept in one place so the on-device engine
 * ([LlmIngredientEngine]) and the online engine ([RemoteIngredientEngine]) ask
 * for the EXACT same JSON shape. That single contract means one set of parsers
 * ([FoodJsonParser]) can read either engine's output.
 *
 * These were originally inline in [LlmIngredientEngine]; nothing about the wording
 * changed when they moved here.
 */
object FoodPrompts {

    /** Text path: turn a typed meal/food description into foods. */
    fun describe(food: String): String = """
        The user ate or drank: "$food".
        Identify the foods/drinks. For a packaged product or a DRINK (e.g. "Coca-Cola", "orange
        juice", "water"), return it as ONE food with "isDrink":true and a single item — do NOT
        split it into chemical ingredients. For a home-cooked or composite dish (e.g. "chilli con
        carne", "okroshka"), return ONE food with "isDrink":false whose "items" are its REAL
        defining ingredients, each with its OWN realistic weight in grams that reflects the actual
        recipe — the items should have DIFFERENT weights (not all the same) and add up to the
        dish's total. Include the ingredients that genuinely define the dish (e.g. okroshka =
        potatoes, cucumber, radish, boiled egg, ham or sausage, fresh dill and spring onion, and a
        kvass or kefir base). Inside a dish's items, NEVER list cooking water, cooking alcohol, or
        anything not actually eaten. Things the user names separately are separate foods ("fish and
        chips with a side salad" = "Fish and chips" + "Side salad"); a sauce or dressing they mention
        is an item INSIDE the dish it comes with. A ready-made sauce, dressing, condiment, cheese or
        bread is ONE item named as itself ("Tartar sauce"), never split into its recipe. Anything that is drunk — water, coffee, tea, juice, soda, energy drinks, milk, kefir, smoothies, shakes (protein shakes too), beer, wine, cocktails — is a DRINK: "isDrink":true, amount in ml. If the user states a total weight (e.g.
        "650g"), make the item weights sum to it.
        Then give EVERY dish with 2 or more items a "choices" array with AT LEAST ONE choice (at most
        2): the part of THIS dish the words don't tell you that changes its nutrition most — which
        meat, the cooking fat, fried or baked, the sauce, base or filling ("okroshka" doesn't say
        kefir or kvass, "pierogi" don't say the filling). A single-item food or a drink gets none:
        "choices":[]. Each choice: {"label":"<1-2 words: Meat, Fried in, Base, Filling, Sauce>",
        "item":"<the name of the item in your items you guessed>","options":[2-4 item objects: your
        guess first, then what people commonly use in THIS dish for that part instead]}. An option
        REPLACES that one item: never an extra, never a "healthier" swap, never another dish.
        For each item set "waterMlPer100" = real water content per 100 ml/g (water=100, cola≈89,
        juice≈85, milk≈88; fruit/veg 80-96, soup 88-92, cooked grains 65-70, bread 35, cheese 37,
        nuts 4, oil 0). Estimate honestly for EVERY item, foods included.
        Use realistic calorie densities (kcal per 100 g): light soups 25-60, broths 10-30,
        salads/veg 15-120, fruit 30-90, cooked grains/pasta 110-160, lean meat 120-200,
        cheese/fatty/fried 250-450, oils ~880. A bowl of light soup is NOT calorie-dense.
        Plain water (tap, mineral, sparkling), black coffee, plain tea and diet drinks are 0 kcal.
        Reply with ONLY a JSON object in exactly this format and nothing else:
        {"foods":[{"name":"Coca-Cola","isDrink":true,"grams":330,"items":[{"name":"Coca-Cola","grams":330,"kcalPer100g":42,"proteinPer100g":0,"fatPer100g":0,"carbsPer100g":11,"fiberPer100g":0,"waterMlPer100":89,"vitA":0,"vitC":0,"vitD":0,"calcium":0,"iron":0,"potassium":0,"sodium":5,"b12":0,"folate":0,"b6":0,"magnesium":1,"zinc":0,"vitE":0,"caffeine":10}],"choices":[]}]}
        Use realistic per-100 values and a realistic serving (grams for food, ml for drinks).
        vitA = vitamin A mcg, vitC = vitamin C mg, vitD = vitamin D mcg,
        calcium/iron/potassium/sodium = mg, b12 = vitamin B12 mcg, folate = mcg,
        b6 = vitamin B6 mg, magnesium/zinc = mg, vitE = vitamin E mg. All per 100 g.
        caffeine = mg per 100 g/ml (brewed coffee 40, espresso 210, energy drink 32, cola 10,
        black tea 20, green tea 12, dark chocolate 60); 0 for anything without caffeine.
    """.trimIndent()

    /** Online text path — leaner than [describe] (the capable model needs less coaxing). */
    fun describeOnline(food: String): String = """
        The user ate or drank: "$food". Return ONLY JSON: {"foods":[ ... ]}.
        - A packaged product or DRINK ("Coca-Cola", "orange juice") = ONE food, "isDrink":true, a
          single item — don't split it into chemicals.
        - A home-cooked / composite dish ("chilli con carne", "okroshka") = ONE food, "isDrink":false,
          whose "items" are its REAL defining ingredients with DIFFERENT realistic weights that sum
          to the dish total. Match a stated total weight. Never add cooking water or cooking alcohol
          as a dish's item.
        - Things the user names separately are separate foods ("fish and chips with a side salad" =
          "Fish and chips" + "Side salad"). A sauce or dressing they mention is an item INSIDE the
          dish it comes with. A ready-made product (sauce, dressing, condiment, cheese, bread) is ONE
          item named as itself ("Tartar sauce"), never split into its recipe.
        - Anything that is drunk — water, coffee, tea, juice, soda, energy drinks, milk, kefir, smoothies, shakes (protein shakes too), beer, wine, cocktails — is a DRINK: "isDrink":true, amount in ml.
        - "choices": EVERY dish with 2 or more items gets AT LEAST ONE (up to 3, most important
          first): the parts the words don't tell that change its nutrition most — which meat or fish,
          the cooking fat, fried or baked, a sauce, base or filling, the cheese (okroshka's base:
          kefir / kvass / water with sour cream; a pierogi filling). A single-item food or a drink
          gets none: []. Each {"label":"<1-2 words: Meat, Fried in, Base, Filling, Sauce>","item":"<the
          item in your items you guessed>","options":[2-4 item objects, your guess first]}. An option
          REPLACES that item: never an extra, never a healthier swap, never another dish; only what
          is commonly used in exactly this dish.
        Each item, per 100 g/ml: name, grams (this item's weight), kcalPer100g, proteinPer100g,
        fatPer100g, carbsPer100g, fiberPer100g, waterMlPer100 (water 100, cola 89, juice 85,
        milk 88, fruit/veg 80-96, cooked grains 65-70, bread 35, cheese 37, oil 0), then micros:
        vitA/vitD/b12/folate in mcg; vitC/calcium/iron/potassium/sodium/b6/magnesium/zinc/vitE in mg;
        then caffeine in mg (coffee 40, espresso 210, energy drink 32, cola 10, tea 20; 0 if none).
        Realistic calorie densities (light soup 25-60, salad/veg 15-120, cooked grains 110-160,
        fried/fatty 250-450, oil ~880). Never 0 kcal for a real food, but plain water (tap, mineral,
          sparkling), black coffee, plain tea and diet/zero drinks ARE 0 kcal. Estimate every field honestly.
        Example food: {"name":"Coca-Cola","isDrink":true,"grams":330,"items":[{"name":"Coca-Cola","grams":330,"kcalPer100g":42,"proteinPer100g":0,"fatPer100g":0,"carbsPer100g":11,"fiberPer100g":0,"waterMlPer100":89,"vitA":0,"vitC":0,"vitD":0,"calcium":0,"iron":0,"potassium":0,"sodium":5,"b12":0,"folate":0,"b6":0,"magnesium":1,"zinc":0,"vitE":0,"caffeine":10}],"choices":[]}
    """.trimIndent()

    /**
     * "Edit with AI": re-evaluate a WHOLE already-identified food from a natural-language correction
     * ("there are also beans", "no cheese", "it's grilled not fried"). Unlike add-ingredient, the
     * model rebalances every item's weight to keep the dish's total realistic — adding, removing,
     * renaming or re-weighing items — rather than just appending. Shares the item JSON contract.
     */
    fun editFood(food: DetectedFood, instruction: String): String {
        val unit = if (food.isDrink) "ml" else "g"
        val total = food.totalGrams.toInt()
        val current = food.ingredients.joinToString("\n") { ing ->
            "- ${ing.name}: ${ing.grams.toInt()}$unit (${ing.caloriesPer100g.toInt()} kcal/100$unit)"
        }
        return """
            A user is correcting a dish already identified from a photo: "${food.label}"
            (${if (food.isDrink) "a drink" else "a food"}), total about $total $unit, made of:
            $current
            ${notSureAbout(food)}
            The user's correction: "$instruction".

            Re-evaluate the WHOLE dish with this correction. You may ADD, REMOVE, RENAME or RE-WEIGH
            items so the result is realistic and matches what the user said. Keep the items the user
            did NOT mention. Keep the dish's TOTAL weight about the same ($total $unit) UNLESS the
            correction clearly changes the amount — so if you ADD an item, take weight from the
            others so the items still sum to roughly the total, instead of just piling it on top.

            Reply with ONLY JSON in this exact shape and nothing else:
            {"foods":[{"name":"<dish name>","isDrink":${food.isDrink},"grams":$total,"items":[{"name":"<item>","grams":<weight>,"kcalPer100g":0,"proteinPer100g":0,"fatPer100g":0,"carbsPer100g":0,"fiberPer100g":0,"waterMlPer100":0,"vitA":0,"vitC":0,"vitD":0,"calcium":0,"iron":0,"potassium":0,"sodium":0,"b12":0,"folate":0,"b6":0,"magnesium":0,"zinc":0,"vitE":0,"caffeine":0}],"choices":[]}]}
            Per 100 $unit for each item: realistic kcalPer100g (light soup 25-60, salad/veg 15-120,
            cooked grains 110-160, lean meat 120-200, cheese/fried 250-450, oil ~880 — never 0 for a
            real food; plain water, black coffee, plain tea and diet drinks ARE 0), macros, fiber, waterMlPer100 (water 100, cola 89, juice 85, milk 88,
            fruit/veg 80-96, cooked grains 65-70, bread 35, cheese 37, oil 0), micros
            (vitA/vitD/b12/folate in mcg; vitC/calcium/iron/potassium/sodium/b6/magnesium/zinc/vitE in mg),
            and caffeine in mg (coffee 40, espresso 210, energy drink 32, cola 10, tea 20; 0 if none).
            Estimate every field honestly. Never add cooking water or cooking alcohol as an item.
            A ready-made sauce, dressing, condiment, cheese or bread stays ONE item named as itself —
            never split it into its recipe.
        """.trimIndent()
    }

    /**
     * The dish's "Not sure about" groups for [editFood] (the 4 Nov review, #8), so a correction can
     * point at them — "add everything from those variations", "it was the cabbage one". Only the groups
     * the user can see: those whose chosen option is still one of the dish's items. Asks for what's
     * still uncertain back in "choices" (read by `FoodJsonParser.parseChoices`). Empty when there are none.
     */
    internal fun notSureAbout(food: DetectedFood): String {
        val groups = food.choices.mapNotNull { g ->
            val now = g.current ?: return@mapNotNull null
            val inDish = food.ingredients.firstOrNull { it.name.equals(now.name, ignoreCase = true) } ?: return@mapNotNull null
            val options = g.options.mapIndexed { i, o ->
                if (i == g.selected) "${o.name} (in the dish now, ${inDish.grams.toInt()}${if (inDish.isDrink) "ml" else "g"})"
                else "${o.name} (${o.caloriesPer100g.toInt()} kcal/100${if (o.isDrink) "ml" else "g"})"
            }
            "- ${g.label}: " + options.joinToString(" | ")
        }
        if (groups.isEmpty()) return ""
        return "\n" + """
NOT SURE ABOUT: what couldn't be told from the photo, shown to the user under the dish as choices.
The user may call these "variations", "options", "those", "them" or use a group's name:
${groups.joinToString("\n")}
If the correction refers to them, use these options (their names and nutrition). When the user wants
several options of one group at once ("all of them", "both", "everything from those"), put each in as
its own item and split the weight of the one in the dish now between them; keep the dish's total unless
they say there was more.
In "choices" return ONLY what is still uncertain after the correction, in the same shape:
{"label":"<the group>","item":"<the item in your items>","options":[that item first, then the other
options, as item objects]}. Drop a group the correction settles; [] if nothing is left uncertain.
""".trimIndent() + "\n"
    }

    /** Vision path: identify every food in a photo. [note] is optional user context. */
    fun vision(note: String): String {
        val noteLine = if (note.isBlank()) "" else
            "The user added this context — trust it and use it to be accurate: \"$note\".\n\n"
        return noteLine + """
        Look closely at THIS photo and identify EVERY distinct food and drink you actually see,
        INCLUDING sauces, creams, syrups, toppings and garnishes (for example whipped cream,
        caramel or chocolate sauce, jam, ice cream, nuts, fruit). List EACH separate thing as its
        OWN entry in "foods". Describe only what is visibly present - do NOT invent foods that are
        not in the picture. If a coin, bank card or fork is visible, use it for SCALE to judge real
        portion sizes (a bank card is 8.6 cm wide, a typical fork ≈19 cm long, a 1-euro/quarter coin
        ≈2.4 cm across).
        Mark drinks with "isDrink":true (measured in ml); solid foods use "isDrink":false.
        Anything that is drunk — water, coffee, tea, juice, soda, energy drinks, milk, kefir, smoothies, shakes (protein shakes too), beer, wine, cocktails — is a DRINK: "isDrink":true, amount in ml. A glass, cup, bottle or can of ANY drink you can
        see — plain water, beer and wine included — is its OWN entry in "foods", never folded into a dish.
        ALWAYS set "waterMlPer100" to the item's real water content per 100 g/ml: drinks water=100,
        cola=89, juice=85, milk=88; foods — most fruit/veg 80-96 (watermelon 92, cucumber 96,
        orange 86, apple 85), soups 88-92, cooked rice/pasta 65-70, bread 35, cheese 37, nuts 4,
        oil 0. Estimate it honestly for EVERY item, foods included.
        Give REALISTIC per-100 g nutrition for every item - NEVER 0 kcal for a real food (plain water, black coffee,
        plain tea and diet drinks ARE 0 kcal), and do NOT over-estimate: light soups are 25-60 kcal/100g, broths 10-30, salad/veg 15-120, cooked
        grains 110-160, fried/oily food 250-450. A bowl of light soup is not calorie-dense.
        If a food is a blended or mixed dish whose parts you can't see one-by-one (a soup, stew,
        smoothie or sauce) and you can tell what it is (use the user's note if given), list its
        TYPICAL ingredients as separate items with realistic individual weights instead of one
        opaque entry — e.g. okroshka = potato, cucumber, boiled egg, ham, dill, kvass or kefir. A
        smoothie or shake stays ONE food with "isDrink":true, its ingredients as its items.
        Inside a dish's items never list cooking water or cooking alcohol.
        A ready-made sauce, dressing, condiment, cheese or bread is ONE entry named as itself
        ("Tartar sauce", "Ketchup", "Pesto") — NEVER split it into its recipe (tartar sauce is not
        mayonnaise + pickles + capers).
        Reply with ONLY a compact JSON object and no other text. Follow this example EXACTLY in
        shape, but replace the foods and numbers with the real ones you see (these numbers are a
        realistic crepes-with-toppings-and-a-latte example):
        {"foods":[
        {"name":"Crepe","isDrink":false,"grams":120,"items":[{"name":"Crepe","grams":120,"kcalPer100g":220,"proteinPer100g":6,"fatPer100g":8,"carbsPer100g":31,"fiberPer100g":1,"waterMlPer100":0,"vitA":30,"vitC":0,"vitD":1,"calcium":80,"iron":1,"potassium":120,"sodium":150,"b12":0.3,"folate":10,"b6":0.05,"magnesium":12,"zinc":0.5,"vitE":0.4,"caffeine":0}]},
        {"name":"Whipped cream","isDrink":false,"grams":40,"items":[{"name":"Whipped cream","grams":40,"kcalPer100g":340,"proteinPer100g":2,"fatPer100g":36,"carbsPer100g":3,"fiberPer100g":0,"waterMlPer100":0,"vitA":110,"vitC":0,"vitD":0,"calcium":65,"iron":0,"potassium":75,"sodium":30,"b12":0.2,"folate":3,"b6":0.02,"magnesium":7,"zinc":0.2,"vitE":0.5,"caffeine":0}]},
        {"name":"Caramel sauce","isDrink":false,"grams":25,"items":[{"name":"Caramel sauce","grams":25,"kcalPer100g":310,"proteinPer100g":1,"fatPer100g":9,"carbsPer100g":57,"fiberPer100g":0,"waterMlPer100":0,"vitA":0,"vitC":0,"vitD":0,"calcium":40,"iron":0,"potassium":80,"sodium":200,"b12":0,"folate":0,"b6":0,"magnesium":5,"zinc":0.1,"vitE":0,"caffeine":0}]},
        {"name":"Strawberries","isDrink":false,"grams":50,"items":[{"name":"Strawberries","grams":50,"kcalPer100g":33,"proteinPer100g":1,"fatPer100g":0,"carbsPer100g":8,"fiberPer100g":2,"waterMlPer100":91,"vitA":1,"vitC":59,"vitD":0,"calcium":16,"iron":0,"potassium":150,"sodium":1,"b12":0,"folate":24,"b6":0.05,"magnesium":13,"zinc":0.1,"vitE":0.3,"caffeine":0}]},
        {"name":"Latte","isDrink":true,"grams":250,"items":[{"name":"Latte","grams":250,"kcalPer100g":54,"proteinPer100g":3,"fatPer100g":2.5,"carbsPer100g":4.5,"fiberPer100g":0,"waterMlPer100":88,"vitA":30,"vitC":0,"vitD":0.5,"calcium":110,"iron":0,"potassium":150,"sodium":40,"b12":0.4,"folate":5,"b6":0.04,"magnesium":12,"zinc":0.4,"vitE":0,"caffeine":30}]}
        ]}
        Units per 100 g: vitA/vitD/b12/folate = mcg; vitC/calcium/iron/potassium/sodium/b6/magnesium/zinc/vitE = mg.
        caffeine = mg per 100 g/ml: coffee 40, espresso 210, latte 30, energy drink 32, cola 10,
        black tea 20, green tea 12, dark chocolate 60; 0 for anything without caffeine.
        Estimate every field honestly; use 0 only when truly absent.
    """.trimIndent()
    }

    /**
     * Online vision prompt — purpose-built for a capable cloud model, so it's leaner than the
     * on-device [vision] prompt (no repeated coaxing / four worked examples). In ONE pass it reads
     * the plate, names composite dishes FROM THE PHOTO, offers choices for what it can't see, and — only when the
     * whole plate is a single dish it's unsure about — offers 2-3 candidate dishes to pick from.
     */
    fun visionOnline(note: String): String {
        val noteLine = if (note.isBlank()) "" else
            "User context (trust it): \"$note\".\n\n"
        return noteLine + """
        Identify every food and drink in this photo, including sauces, creams, syrups, toppings and
        garnishes. Describe only what's visibly present. Reply with ONLY this JSON:
        {"foods":[...],"dishCandidates":[...]}

        Each food is one DISH: {"name","isDrink","grams","items":[ item objects ],"choices":[]}.
        - A dish is what you'd order or serve on its own: a main, a side (fries, rice, a side salad), a
          dessert, a bread basket. Fish and chips with a side salad = two foods, "Fish and chips" and
          "Side salad". Every drink is its own food.
        - Name cooked or assembled dishes by their REAL name ("Okroshka", "Caesar salad", "Pad Thai")
          with their true ingredients in "items".
        - Sauces, dressings, dips, toppings and garnishes go INSIDE the dish they're served with, as one
          of its items, also when they sit in a small pot beside it ("Fish and chips" items: cod, batter,
          fries, tartar sauce). Only a sauce with no dish to go with is its own food.
        - A ready-made product (a sauce, dressing, condiment, cheese, bread, a packaged food) is ONE item
          named as itself ("Tartar sauce", "Ketchup", "Pesto"), never split into its recipe (no
          mayonnaise + pickles + capers for tartar sauce).
        - "choices": EVERY dish with 2 or more items gets AT LEAST ONE (up to 3 per dish, most
          important first): the parts you can't be sure of from the photo that change its nutrition
          most — which meat or fish (a breaded cutlet: pork / chicken / veal), the cooking fat, fried
          or baked, a hidden filling (pierogi, dumplings, pies), a soup's base (okroshka: kefir / kvass
          / water with sour cream), a sauce, the cheese, tomato or red pepper. A single-item food or a
          drink gets none: []. Each {"label":"<1-2 words: Meat, Fried in, Filling, Base, Sauce>",
          "item":"<the item in this dish's items you guessed>","options":[2-4 item objects, your guess
          first]}. An option REPLACES that item: never an extra or a topping to add, never a
          healthier swap, never another dish, never something the photo rules out (no brown rice
          when the rice is clearly white). Only what is commonly used in exactly this dish.
        - Mark drinks "isDrink":true (ml); solids false (g). Anything that is drunk — water, coffee, tea, juice, soda, energy drinks, milk, kefir, smoothies, shakes (protein shakes too), beer, wine, cocktails — is a DRINK: "isDrink":true, amount in ml. Every drink
          you can see (plain water, beer and wine too) is its OWN food — never part of a dish.
        - Use any fork/coin/bank card in shot for SCALE (a bank card is 8.6 cm wide) to judge real portions.

        Each item object, all per 100 g/ml: name, grams (this item's own weight), kcalPer100g,
        proteinPer100g, fatPer100g, carbsPer100g, fiberPer100g, waterMlPer100 (water 100, cola 89,
        juice 85, milk 88, most fruit/veg 80-96, soup 88-92, cooked grains 65-70, bread 35,
        cheese 37, nuts 4, oil 0), then micros: vitA/vitD/b12/folate in mcg; and
        vitC/calcium/iron/potassium/sodium/b6/magnesium/zinc/vitE in mg; then caffeine in mg
        (coffee 40, espresso 210, latte 30, energy drink 32, cola 10, tea 20; 0 if none).
        Realistic calorie densities: light soup 25-60, broth 10-30, salad/veg 15-120, fruit 30-90,
        cooked grains/pasta 110-160, lean meat 120-200, cheese/fried 250-450, oil ~880. Never 0 kcal
        for a real food (plain water, black coffee, plain tea and diet/zero drinks ARE 0 kcal); never list cooking water or cooking alcohol as a dish's item. Estimate every field honestly.
        Example item: {"name":"Crepe","grams":120,"kcalPer100g":220,"proteinPer100g":6,"fatPer100g":8,"carbsPer100g":31,"fiberPer100g":1,"waterMlPer100":0,"vitA":30,"vitC":0,"vitD":1,"calcium":80,"iron":1,"potassium":120,"sodium":150,"b12":0.3,"folate":10,"b6":0.05,"magnesium":12,"zinc":0.5,"vitE":0.4,"caffeine":0}

        "dishCandidates": include ONLY when the food on the plate is a single dish you're genuinely unsure
        about — 2-3 most likely dish names, each {"name","choices":[ choice objects, same rule ]}.
        Drinks are never part of a candidate dish (they stay their own foods).
        Use [] when it's clearly one dish, or several separate foods.
    """.trimIndent()
    }

    /** Second pass: name the dish from its loose ingredients (and photo, if available). */
    fun dish(ingredients: List<Ingredient>, note: String): String {
        val names = ingredients.joinToString(", ") { it.name }
        val noteLine = if (note.isBlank()) "" else
            "\n            The user added this context — use it: \"$note\"."
        return """
            Look at THIS photo. These items were found on the plate: $names.
            Use BOTH the photo and this list to name the actual DISHES. A dish is what you'd order or
            serve on its own: a main, a side (fries, rice, a side salad), a dessert. Fish and chips with
            a side salad = TWO dishes. Sauces, dips and toppings belong INSIDE the dish they come with.
            Give ONE entry per separate dish, with "items" = exactly which of the listed items belong to it
            (copy their names; every listed item in one dish). If it's all one dish, give one entry and put
            1-2 other likely names for it in "also" (e.g. a round base with dough, cheese, ham and
            mushrooms = Pizza, also ["Calzone"]).$noteLine
            EVERY dish with 2 or more items gets "choices" with AT LEAST ONE choice (at most 2): the
            part you can't be sure of from the photo that changes its nutrition most (which meat, the
            cooking fat, a hidden filling, a soup's base, a sauce, tomato or red pepper):
            {"label":"<Meat, Fried in, Filling, Base or Sauce>","item":"<the listed item you're unsure
            about>","options":[2-4 item objects: that item first, then what people commonly use in THIS
            dish instead]}. An option REPLACES that item: never an extra, never another dish, never
            something the photo rules out. A dish of one item gets "choices":[].
            NEVER suggest alcohol, plain water, or anything not actually eaten.
            Give each option a realistic per-100 nutrition (light soups 25-60 kcal/100g, oils ~880).
            Reply with ONLY a JSON object in exactly this format and nothing else:
            {"dishes":[{"name":"<dish name>","items":["<listed item>"],"also":[],"choices":[{"label":"Filling","item":"<listed item>","options":[{"name":"<option>","grams":100,"kcalPer100g":200,"proteinPer100g":6,"fatPer100g":8,"carbsPer100g":26,"fiberPer100g":2,"vitA":0,"vitC":0,"vitD":0,"calcium":20,"iron":1,"potassium":150,"sodium":300,"b12":0,"folate":10,"b6":0.1,"magnesium":15,"zinc":0.5,"vitE":0.5,"caffeine":0}]}]}]}
        """.trimIndent()
    }

    /** Third pass: health insights for an identified meal. */
    /**
     * The meal's real ingredients, spelled out for the insights prompts. Without this the model
     * only sees a dish name and macros, so it guesses at what's inside — which is how "swap the
     * mayonnaise" ends up on a plate that never had any.
     */
    private fun ingredientLine(foods: List<DetectedFood>): String {
        val parts = foods.flatMap { it.ingredients }.map { it.name.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""
        return "It is made of exactly these and nothing else: ${parts.distinct().joinToString(", ")}."
    }

    fun insights(foods: List<DetectedFood>): String {
        val summary = foods.joinToString("; ") { food ->
            "${food.label} (${food.totalCalories.toInt()} kcal, P${food.totalProtein.toInt()}g, " +
                "F${food.totalFat.toInt()}g, C${food.totalCarbs.toInt()}g, Fiber ${food.totalFiber.toInt()}g)"
        }
        return """
            A user just logged this meal: $summary.
            ${ingredientLine(foods)}
            Analyze it and reply with ONLY a JSON object:
            {"healthScore":<1-10>,"scoreFactors":[{"text":"<reason>","positive":<true/false>}],"swaps":[{"from":"<ingredient>","to":"<healthier option>","why":"<short benefit>"}],"energy":"<1-2 sentences on energy impact>","energyScore":<1-5>,"mood":"<1-2 sentences on mood impact>","moodScore":<1-5>,"pairings":["<suggestion 1>","<suggestion 2>"]}
            Rules:
            - healthScore: 1=very unhealthy, 10=excellent. Consider balance, fiber, vitamins, sodium, processing.
            - scoreFactors: 2-5 short reasons. Mix positives and negatives. Be specific.
            - swaps: only if score<10. Suggest 1-3 ingredient swaps that would raise the score. Skip if score is 10.
              "from" MUST be one of the ingredients listed above. NEVER suggest replacing something
              that isn't in this meal. If nothing here is worth swapping, return an empty array.
            - energy: how this meal affects energy over the next few hours (sugar crash? sustained? etc).
            - energyScore: 1=heavy/sleepy or a sugar crash, 3=neutral, 5=light and energizing.
            - mood: how the nutrients may affect mood (tryptophan, omega-3, sugar, etc). Be honest but kind.
            - moodScore: 1=likely to drag mood down, 3=neutral, 5=mood-lifting.
            - pairings: 1-3 foods/drinks that complement this meal nutritionally (add what's missing — e.g. fiber, vitamins, protein). Empty array if the meal is already well-rounded.
        """.trimIndent()
    }

    /**
     * Richer online insights prompt — same JSON shape as [insights] (so one parser reads both),
     * but it asks a capable, deeper-thinking model for more specific, genuinely useful analysis.
     */
    fun insightsOnline(foods: List<DetectedFood>): String {
        val summary = foods.joinToString("; ") { food ->
            "${food.label} (${food.totalCalories.toInt()} kcal, P${food.totalProtein.toInt()}g, " +
                "F${food.totalFat.toInt()}g, C${food.totalCarbs.toInt()}g, Fiber ${food.totalFiber.toInt()}g)"
        }
        return """
            A user logged this meal: $summary.
            ${ingredientLine(foods)}
            Think it through, then reply with ONLY this JSON:
            {"healthScore":<1-10>,"scoreFactors":[{"text":"<reason>","positive":<bool>}],"swaps":[{"from":"<x>","to":"<y>","why":"<benefit>"}],"energy":"<1-2 sentences>","energyScore":<1-5>,"mood":"<1-2 sentences>","moodScore":<1-5>,"pairings":["<idea>"]}
            Make it specific and genuinely useful (not generic):
            - scoreFactors: 3-5 concrete reasons that cite the actual nutrients/amounts at play. Mix positives and negatives.
            - swaps: 1-3 realistic swaps that would meaningfully raise the score, each with the concrete benefit. Empty if the meal is already excellent.
              Every "from" MUST be an ingredient listed above — never propose replacing something that isn't in this meal.
            - energy: the likely blood-sugar curve + satiety over the next few hours — be specific about why.
            - mood: a plausible mood effect (tryptophan, omega-3, sugar swings, magnesium, …), honest but kind.
            - pairings: 1-3 foods/drinks that fill THIS meal's real gaps — name the missing nutrient. [] if well-rounded.
            Scores: energyScore 1=crash/heavy → 5=light & energizing; moodScore 1=drags mood → 5=lifts it.
        """.trimIndent()
    }

    /**
     * What the coach puts first, how it reads the person behind the numbers, and when food may be
     * suggested — the same for both review prompts. Added after the coach circled protein for days while
     * saying nothing about an 1,800 kcal dinner, never picked up on stress or boredom eating, and once
     * suggested a protein meal at 11 PM in the middle of the user's fast.
     */
    private fun coachingPrinciples(isDaily: Boolean): String {
        val scope = if (isDaily) "day" else "period"
        return """
            PRIORITIES — lead with the biggest story:
            - Decide what mattered most for their goal this $scope, starting from WHAT STOOD OUT. A meal far bigger
              than usual, an over-budget day, eating after the budget was used up, or a broken fast outranks a protein
              or fibre shortfall. Protein matters, but it is not the story every time — when something bigger
              happened, talk about that first and keep nutrient targets to a sentence.
            - Don't circle one theme across overviews. If your earlier coaching kept returning to the same topic,
              move on unless it is genuinely still the biggest issue.
            THE PERSON BEHIND THE NUMBERS:
            - Eating isn't only about hunger. A meal much bigger than usual, eating after the budget was used up,
              late-evening eating, evening or weekend spikes and grazing often have a cause: real hunger from eating
              too little earlier, stress, boredom, tiredness, a social setting, habit. Be curious about the WHY, not
              only the food.
            - When what you know about them, or their answers, name a trigger (stress, boredom, tiredness, social plans…),
              engage with it directly and give ONE practical tool for that moment — a short pause to ask "am I hungry,
              bored, stressed or tired?", a planned evening snack inside their eating window instead of open-ended
              grazing, a non-food thing for the trigger moment (a walk, a call, a shower, a tea), trigger foods out of
              sight, or eating enough earlier so evening hunger doesn't pile up. Kind and practical, never preachy.
            - You are a coach, not a clinician: never diagnose. If what they describe sounds like a persistent loss of
              control around food, gently mention that talking to a professional can really help.
            TIMING — respect when they eat:
            - Never suggest eating outside their eating window or close to bedtime (see YOUR DAY & FASTING) just to
              reach a number. Anything you'd add goes into meals inside the window, or the shortfall is simply let go
              for that day.
        """.trimIndent()
    }

    /** The FOCUS / WATCH / QUESTION / ANSWERS lines both review prompts end with (parsed by [ReviewText]). */
    private fun closingLines(period: String, isDaily: Boolean): String {
        val next = if (isDaily) "day" else period
        return """
            FOCUS: <the single most useful thing to DO going forward, with a concrete example — a specific food, swap or habit>
            WATCH: <the single most important pattern or risk to keep an eye on, with why it matters and a concrete fix>
            QUESTION: <ONE short, open question (under 20 words) about the biggest UNEXPLAINED moment in WHAT STOOD OUT — what led to it, what was going on, how they felt. Prefer the situation or feeling behind a choice over food preferences. A NEW topic, not one of your recent questions, and nothing their answers already told you. If nothing stood out, ask what would make the next $next easier.>
            ANSWERS: <2-4 likely short answers they could tap, each under 6 words, separated by " | " — for a "why" question cover the real range, hunger AND emotional reasons, e.g. "Really hungry | Stressed | Bored | Social plans">
        """.trimIndent()
    }

    /** Deep, personalised nutrition review for a period. Returns plain text (not JSON). */
    fun review(
        period: String,
        rows: List<DailyNutritionRow>,
        totalDays: Int,
        profile: UserProfile,
        targets: DailyTargets?,
        weights: List<WeightEntryEntity>,
        foodLog: String = "",
        /** Extra context assembled by ReviewGenerator: activity/burn, personal limitations, long-term habits. */
        extraContext: String = ""
    ): String {
        val goalLabel = profile.fitnessGoal.label
        val goalDesc = profile.fitnessGoal.description
        val sex = profile.sex.label
        val age = profile.ageYears
        val height = profile.heightCm.toInt()

        val dayLines = rows.joinToString("\n") { r ->
            "${r.date}: ${r.calories.toInt()} kcal, P${r.protein.toInt()}g, F${r.fat.toInt()}g, C${r.carbs.toInt()}g, Fiber ${r.fiber.toInt()}g"
        }

        val targetLine = targets?.let {
            "Daily targets: ${it.calories} kcal, P${it.proteinG}g, F${it.fatG}g, C${it.carbsG}g, Fiber ${it.fiberG}g"
        } ?: "Targets: not set"

        val weightLine = if (weights.isNotEmpty()) {
            val recent = weights.takeLast(5)
            "Recent weights: " + recent.joinToString(", ") { "${it.date}: ${it.weightKg}kg" }
        } else "No weight data"

        val daysLogged = rows.size
        val daysSkipped = totalDays - daysLogged

        val isDaily = period.equals("daily", ignoreCase = true) || totalDays <= 1

        val instructions = if (isDaily) {
            """
            Write a genuinely useful coaching note in flowing prose — no headings, no numbered list,
            no bullets. Work through this internally, IN THIS ORDER:

            - FIND THE DAY'S BIGGEST STORY. Start from WHAT STOOD OUT: an oversized meal, eating past the
              budget, a late meal or a broken fast is what the note is about — not whichever nutrient fell short.
            - FRAME THE DAY IN CONTEXT. Read the ACTIVITY data before the food. A big step count,
              a workout, or an "unusually active day" flag almost always shaped how they ate, and
              intake must be judged NET of what they burned — a day that looks low can be a real
              deficit once you subtract the burn, or a sensible refuel after a hard effort. Do NOT
              open by reciting numbers the user can already see on their screen.
            - GIVE CREDIT before any correction. Name at least one real thing they did well — a good
              food choice, hitting protein, a smart improvised call on a disrupted day. Sensible
              choices under hard circumstances ARE a win; say so.
            - EXPLAIN, don't just assert. When something matters, give the one-line MECHANISM and tie
              it to THIS person's goal (e.g. "protein this high is what preserves muscle while you're
              in a deficit"). They should leave understanding WHY, not just told what to do.
            - THE ONE CHANGE THAT MOVES THEM FORWARD. Offer a single concrete, doable adjustment with a
              real example — a specific food, swap or habit for the moment that went wrong, grounded in
              THEIR data — framed as advice ("a strong option would be…"), not an order. If the day and
              week both look fine, say so plainly and do not invent a problem.
            """.trimIndent()
        } else {
            """
            Write a genuinely useful coaching note in flowing prose — no headings, no numbered list,
            no bullets. Work through this internally:

            - THE TRAJECTORY IN CONTEXT. The real story of this $period — weight trend vs. the calorie
              AND activity trend together, not a day-by-day recap. Judge intake against activity; read
              the weight trend against training. Check WHAT STOOD OUT: a few oversized meals or
              over-budget days often explain the whole $period.
            - WHAT'S WORKING. Name the genuine strengths and consistent good habits first — the
              patterns worth protecting.
            - WHAT MATTERS AND WHY. Pick the one pattern that actually affects their goal and explain
              the mechanism, so they understand the stakes — not just "eat more protein" but why it
              matters for where they're trying to go.
            - THE ONE HIGH-LEVERAGE CHANGE. One concrete, doable shift for next $period with a real
              example from their own data — or "stay the course" if things are working. Never a
              checklist.
            """.trimIndent()
        }

        return """
            You are a calm, knowledgeable nutrition coach who happens to know this user well.

            USER PROFILE: $sex, $age years old, ${height}cm.
            GOAL: $goalLabel — $goalDesc.
            $targetLine
            $weightLine
            Days in period: $totalDays. Days with data: $daysLogged. Days with no food logged: $daysSkipped.

            NUTRITION DATA ($period):
            $dayLines

            ${if (foodLog.isBlank()) "" else "FOODS ACTUALLY EATEN (reference these by name):\n$foodLog"}

            ${if (dayLines.isEmpty()) "No food was logged during this period." else ""}
            ${if (extraContext.isBlank()) "" else extraContext}

            $instructions

            ${coachingPrinciples(isDaily)}

            HOW TO WRITE IT:
            - You are an experienced, level-headed nutrition coach who explains their reasoning — a
              knowledgeable guide, NOT a fitness influencer, a robot or a cheerleader. You're not a
              therapist either, but you do coach the habits and feelings around food. Advise; don't
              command.
            - NEVER restate a number or food the user can already see on their screen unless you add
              meaning to it. Every sentence must earn its place: an insight, a reason, or an action.
            - Judge intake NET of the ACTIVITY & CALORIES BURNED above, and treat an unusually active
              day as the CENTRE of the story, not a footnote — credit the effort and the choices it
              forced (improvised snacks, no time for real meals).
            - Give credit before correction. Be emotionally neutral about food — no moral judgments; a
              calorie-dense meal is just calorie-dense, not "bad".
            - Explain the MECHANISM behind any advice and tie it to THIS user's goal — teach, don't
              just tell.
            - Read weight change against training: weight lost with little/no resistance work may be
              muscle, not just fat — say so; steady weight on a real deficit while training hard can be
              recomposition, not failure. A deficit that isn't showing on the scale is usually
              water/glycogen (±0.5–1 kg) hiding slow fat loss, plus a burn estimate that runs high and
              logging that runs low — not a reason to eat even less.
            - Recommend STRATEGIES with concrete examples, never branded products, and clock-times only
              to respect their eating window or bedtime.
              Respect the PERSONAL CONTEXT / LIMITATIONS; never suggest what they rule out. Keep it
              realistic and SUSTAINABLE. If nothing needs changing, say so and stop — don't manufacture
              a problem.
            - Refer to the day/period by the REVIEWING date above; never "today"/"yesterday"/"tomorrow".
            - Whether a target was met comes ONLY from the TARGET CHECK above — never contradict it.
            - NEVER use: "fat-bomb", "solid win", "brilliant move", "treat yourself", "cheat day",
              "cheat meal", "guilty pleasure", "naughty", "clean eating", "be patient with yourself",
              "crushing it".
            - Length: ${if (isDaily) "roughly 250–400 words" else "roughly 300–450 words"} of flowing
              prose — thorough enough to actually explain, never padded.

            After the prose (and BEFORE the MEMORY line if you were asked for one), output exactly these
            four lines — each on its own line. FOCUS and WATCH are each ONE sentence under ~30 words,
            grounded in a SPECIFIC number/food/pattern from THIS data, never generic, and never a repeat of
            earlier coaching unless you say why it still matters:
            ${closingLines(period, isDaily)}
        """.trimIndent()
    }

    /** Leaner online nutrition review — the deeper-thinking cloud model needs less hand-holding. */
    fun reviewOnline(
        period: String,
        rows: List<DailyNutritionRow>,
        totalDays: Int,
        profile: UserProfile,
        targets: DailyTargets?,
        weights: List<WeightEntryEntity>,
        foodLog: String = "",
        /** Extra context assembled by ReviewGenerator: activity/burn, personal limitations, long-term habits. */
        extraContext: String = ""
    ): String {
        val goalLabel = profile.fitnessGoal.label
        val dayLines = rows.joinToString("\n") { r ->
            "${r.date}: ${r.calories.toInt()} kcal, P${r.protein.toInt()}g, F${r.fat.toInt()}g, C${r.carbs.toInt()}g, Fiber ${r.fiber.toInt()}g"
        }
        val targetLine = targets?.let {
            "Targets/day: ${it.calories} kcal, P${it.proteinG}g, F${it.fatG}g, C${it.carbsG}g, Fiber ${it.fiberG}g"
        } ?: "Targets: not set"
        val weightLine = if (weights.isNotEmpty())
            "Recent weights: " + weights.takeLast(5).joinToString(", ") { "${it.date}: ${it.weightKg}kg" }
        else "No weight data"
        val daysLogged = rows.size
        val daysSkipped = totalDays - daysLogged
        val isDaily = period.equals("daily", ignoreCase = true) || totalDays <= 1
        val scope = if (isDaily) "this single day" else "this $period"
        val framework = if (isDaily)
            """
            Coach this day in flowing prose (no headings, no numbered list). Internally, in order:
            - Find the day's biggest story in WHAT STOOD OUT (an oversized or late meal, eating past the
              budget, a broken fast) — that is what the note is about, not whichever nutrient fell short.
            - Frame the day in its ACTIVITY context — judge intake NET of steps/workouts; an
              unusually active day (big step count) reshaped how they ate, so make it the story, not a
              footnote. Don't recite numbers the user can already see.
            - Give credit before correction — name a real thing they did well, especially a smart
              choice forced by the day.
            - Explain the MECHANISM behind what matters and tie it to their goal — teach, don't just tell.
            - Offer ONE concrete, doable change with a specific example from their own data — or say
              plainly that nothing needs changing.
            """.trimIndent()
        else
            """
            Coach this $period in flowing prose (no headings, no numbered list). Internally:
            - The trajectory: weight trend vs. the calorie AND activity trend together; judge intake
              against activity, and the weight trend against training. A few oversized meals or
              over-budget days in WHAT STOOD OUT often explain the whole $period.
            - What's working — the genuine strengths and consistent habits worth protecting.
            - What matters and why — the one pattern that actually affects their goal, with the
              mechanism spelled out.
            - ONE high-leverage change for next $period with a real example from their data, or "stay
              the course".
            """.trimIndent()
        return """
            You are a calm, knowledgeable nutrition coach who knows this user well. Review $scope for a
            ${profile.sex.label}, ${profile.ageYears}y, ${profile.heightCm.toInt()}cm, goal "$goalLabel" (${profile.fitnessGoal.description}).
            $targetLine
            $weightLine
            Days in period: $totalDays · logged: $daysLogged · not logged: $daysSkipped.
            Daily totals:
            $dayLines
            ${if (dayLines.isEmpty()) "No food was logged this period." else ""}
            ${if (foodLog.isBlank()) "" else "Foods actually eaten (reference these by name):\n$foodLog"}
            ${if (extraContext.isBlank()) "" else extraContext}

            $framework

            ${coachingPrinciples(isDaily)}

            Coach, don't summarize. Never restate a number or food the user can already see unless you
            add meaning to it — every sentence must earn its place (an insight, a reason, or an action).
            Give credit before correction. Explain the MECHANISM behind any advice and tie it to their
            goal. Judge intake NET of activity/burn, and make an unusually active day the CENTRE of the
            story (credit the improvised choices it forced). Read weight against training (loss with
            little/no resistance work may be muscle; steady weight on a real deficit while training hard
            can be recomposition). A deficit not showing on the scale is usually water/glycogen (±0.5–1
            kg) plus a burn estimate that runs high and logging that runs low — not a reason to eat
            less. Respect the personal context/limitations. Recommend STRATEGIES with concrete examples,
            never brands; clock-times only to respect their eating window or bedtime. Refer to the
            day/period by the REVIEWING date above; never
            "today"/"yesterday"/"tomorrow". Whether a target was met comes ONLY from the TARGET CHECK
            above — never contradict it.

            TONE: an experienced, level-headed coach who explains their reasoning — not an influencer,
            robot, or cheerleader, and not a therapist, though you do coach the habits and feelings
            around food; advise, don't command. Emotionally neutral about food (calorie-dense
            ≠ "bad"). NEVER use: "fat-bomb", "solid win", "brilliant move", "treat yourself", "cheat
            day/meal", "guilty pleasure", "clean eating", "be patient with yourself". If nothing needs
            changing, say so and stop.
            Length: ${if (isDaily) "roughly 250–400" else "roughly 300–450"} words of flowing prose, thorough but not padded.

            After the prose (and BEFORE the MEMORY line if you were asked for one), output exactly these
            four lines. FOCUS and WATCH are each ONE sentence under ~30 words, grounded in a SPECIFIC
            number/food/pattern from this data, never generic, and never a repeat of earlier coaching
            unless you say why it still matters:
            ${closingLines(period, isDaily)}
        """.trimIndent()
    }

    /**
     * Per-item AI insights (used by the meal-detail and collection screens via raw text). Shared
     * by both engines, so the SWAP/ENERGY/MOOD/PAIR line format the screens parse stays stable.
     */
    fun itemInsights(
        name: String,
        amountLabel: String,
        kcal: Int,
        protein: Int,
        fat: Int,
        carbs: Int,
        fiber: Int,
        /** The current ingredient breakdown, so a re-run reflects edited/swapped ingredients. */
        ingredients: String = ""
    ): String = """
        Analyse this food: $name, $amountLabel, $kcal kcal (P${protein}g F${fat}g C${carbs}g Fiber ${fiber}g).
        ${if (ingredients.isBlank()) "" else "Made of these ingredients right now (analyse THESE, not any earlier version): $ingredients."}
        Every SWAP's <original> MUST be this food or one of the ingredients listed above.
        NEVER suggest replacing something that isn't there — write no SWAP line at all instead.
        Reply in EXACTLY this format, one item per line, nothing else:
        SWAP: <original> -> <healthier swap> | <concrete benefit>
        ENERGY: <one sentence on energy over the next few hours>
        ENERGY_SCORE: <1-5, 1=heavy/crash, 5=light & energizing>
        MOOD: <one sentence on mood impact>
        MOOD_SCORE: <1-5, 1=drags mood, 5=lifts it>
        PAIR: <one food or drink that complements it nutritionally>
    """.trimIndent()

    /**
     * Per-item insights for a WHOLE meal in ONE call: a JSON array with one object per food, in the
     * given order. Replaces N separate [itemInsights] calls (one per dish) so a multi-dish meal costs
     * a single request instead of hammering the free-tier per-minute limit. [itemsBlock] is the
     * numbered list of foods; [count] is how many objects the array must contain.
     */
    fun batchItemInsights(itemsBlock: String, count: Int): String = """
        Analyse EACH of these $count foods on its own and return ONE JSON array of exactly $count
        objects, in the SAME order as listed:
        $itemsBlock

        Each object has this shape:
        {"swaps":[{"from":"<the food or one of ITS ingredients>","to":"<healthier option>","why":"<short benefit>"}],
         "energy":"<one sentence on energy over the next few hours>","energyScore":<1-5>,
         "mood":"<one sentence on mood impact>","moodScore":<1-5>,
         "pairings":["<one food or drink that complements it nutritionally>"]}
        Rules:
        - Judge each food on its own — do NOT merge them or analyse the meal as a whole.
        - Every swap's "from" MUST be that food or one of ITS listed ingredients — never something
          that isn't there; use [] when nothing is worth swapping.
        - energyScore: 1=heavy/crash → 5=light & energizing. moodScore: 1=drags → 5=lifts.
        - Reply with ONLY the JSON array of $count objects, same order, nothing else.
    """.trimIndent()

    /** Exercise calorie estimate (used by the exercise screen via raw text). Shared by both engines. */
    fun exerciseEstimate(activity: String, weightKg: Int): String = """
        The user did this exercise: "$activity". Body weight: $weightKg kg.

        Estimate the ACTIVE exercising time, not just the wall-clock time:
        - Many workouts include rest. Strength training, calisthenics, weightlifting, CrossFit,
          circuit and gym sessions have breaks between sets — the actual time exerting is usually
          only about 50–65% of the stated duration. So "1h of calisthenics" is roughly 30–40
          ACTIVE minutes, not 60.
        - Continuous cardio (running, cycling, swimming, brisk walking, rowing, hiking) has no
          real breaks, so ACTIVE minutes ≈ the stated time.
        - If no duration is given, assume a sensible typical session for that activity.

        Reply in EXACTLY this format, nothing else:
        ACTIVITY: <short activity name>
        MINUTES: <effective ACTIVE minutes as an integer>
        MET: <metabolic equivalent, e.g. running 9.8, brisk walk 4.3, weights 5, cycling 7.5>
        TIP: <one short, specific, practical tip about this workout — form, recovery, what to pair it with, or how to progress>
        TIP: <another short tip>
        TIP: <a third short tip>
    """.trimIndent()

    /**
     * ONE meal-aware coaching tip for a just-logged meal, judged against the day so far — or nothing
     * if the meal is fine. Deliberately returns "null" often: a card only appears when it's useful.
     * [dayFacts] is [com.fitpal.app.domain.MealBudgetCheck.factsBlock] (before / this meal / after, and
     * the calorie verdict worked out in code) plus when it was eaten: the model judges, it never sums.
     */
    fun mealCoachingTip(mealFoods: String, dayFacts: String, userGoal: String): String =
        "A user just logged this meal. Give ONE short coaching tip about THIS meal, or nothing.\n" +
            "MEAL (as eaten): $mealFoods\n" +
            dayFacts + "\n" +
            "GOAL: $userGoal\n\n" + """
        Reply with ONLY a JSON object, or the single word null if the meal is fine as-is:
        {"type":"<portion|swap|addition|timing|goal|balance>","message":"<one sentence>"}

        Rules:
        - Return null unless the tip is genuinely useful — a balanced meal that fits the day gets no tip.
        - The CALORIE CHECK was worked out by the app from the numbers above and is correct; don't redo
          the sums. Unless it says the day is OVER, never say the meal is too big, exceeds the budget,
          or should be cut, shared or saved for later.
        - Be specific to THIS meal and culturally sensible (never "add a salad to pelmeni/pasta/sushi").
        - If the day's eating is done (the eating window has closed, or bedtime is near), never suggest
          eating more that day: point at the next meal or the next day instead.
        - Recommend a STRATEGY, not a branded product or an exact clock-time.
        - Neutral tone, no moral words ("bad", "cheat", "guilty"). One sentence, under 25 words.
        - type: portion=too big for the budget (only when the CHECK says OVER), swap=replace a component,
          addition=round it out, timing=when to eat it, goal=how it serves their goal, balance=macro/plate balance.
    """.trimIndent()

    // ---- The meal planner (docs/redesign/10). Online and on-device get their own prompts, as everywhere. ----

    private const val PLAN_ITEM_EXAMPLE =
        """{"name":"Salmon fillet","grams":150,"have":true,"kcalPer100g":208,"proteinPer100g":20,"fatPer100g":13,"carbsPer100g":0,"fiberPer100g":0,"waterMlPer100":0,"vitA":40,"vitC":0,"vitD":11,"calcium":9,"iron":0.3,"potassium":363,"sodium":59,"b12":3.2,"folate":25,"b6":0.6,"magnesium":27,"zinc":0.4,"vitE":3.6,"caffeine":0}"""

    private fun planShape(mealTypes: List<String>) = """
        Reply with ONLY this JSON:
        {"meals":[{"mealType":"<${mealTypes.joinToString("|")}>","name":"<dish name>","cuisine":"<cuisine>","timeMinutes":<total minutes>,"servings":<number>,"difficulty":"easy|medium|hard","whyItFits":"<one short sentence with numbers on why it suits this person today>","ingredients":[ item objects ],"steps":[{"phase":"prep|cook|serve","title":"<2-5 words>","text":"<the full instruction>","uses":["<ingredient with amount>"],"minutes":<only for a waiting step>}],"tips":["<tip>"]}],
         "shopping":[{"name":"<thing to buy>","amount":"<e.g. 200 g>","estimatedCost":<number or null>}]}
    """.trimIndent()

    private fun planAvoidLine(avoid: List<String>) =
        if (avoid.isEmpty()) "" else "Don't repeat these meals: ${avoid.joinToString("; ")}."

    /** Online: one day's meals (or one meal), lean — the facts are in [context]. */
    fun plannerOnline(context: String, dayLabel: String, mealTypes: List<String>, avoid: List<String>): String = """
        You are this person's meal planner. Plan ${if (mealTypes.size == 1) "ONE ${mealTypes.first()}" else "the meals for $dayLabel: ${mealTypes.joinToString(", ")}"} as complete recipes.
        ${planAvoidLine(avoid)}

        $context

        Rules (all are hard rules):
        - NEVER use anything the person is allergic to, or anything their diet excludes. Respect "won't eat".
        - If they said ONLY use what they have, use nothing else (salt, pepper, oil and water are always there).
        - Fit their cooking time, skill and kitchen. Keep within their budget, in their currency.
        - Meals go inside the eating window when fasting is on; never plan food outside it or right before bed.
        - Aim the day at their targets: what's left today for today's plan, the full targets for another day.
        - Real, appetising dishes they'd actually like (use what they love and eat often; vary from recent meals).
        - Ingredients are for the portion ONE person eats ("servings" says how many portions the recipe makes).
          Each item: real grams for that portion and honest per-100 g nutrition; "have": true when it's from
          what they have. List cooking oil with its grams. Sauces and dressings bought ready-made stay one item.
        - Steps a beginner can follow without guessing, in order, grouped by "phase": prep (wash, chop,
          measure, marinate, preheat), cook, serve. Each step: a 2-5 word "title" and a full "text" that
          says HOW MUCH (spoons with grams in brackets: "1 tsp (5 g) paprika", "1 tbsp (10 g) oil"),
          HOW HOT (low / medium / medium-high / high, or °C for the oven), HOW LONG, and HOW TO TELL IT'S
          DONE (colour, texture, internal temperature). "uses": the ingredients that step uses, with
          amounts. "minutes": only when the step is waiting (simmer, bake, rest, marinate), else leave it
          out. Usually 6-14 steps; never skip preheating, seasoning or resting.
        - Tips: 0-2 swaps or shortcuts.
        - shopping: what to buy for these meals (not what they have), with rough cost when there's a budget.
        Example item: $PLAN_ITEM_EXAMPLE

        ${planShape(mealTypes)}
    """.trimIndent()

    /** On-device: the same job, spelled out more for a smaller model. */
    fun planner(context: String, dayLabel: String, mealTypes: List<String>, avoid: List<String>): String = """
        You plan meals. Make ${if (mealTypes.size == 1) "exactly ONE ${mealTypes.first()} recipe" else "one recipe for each of: ${mealTypes.joinToString(", ")} (for $dayLabel)"}.
        ${planAvoidLine(avoid)}

        About the person and their day:
        $context

        Follow these rules exactly:
        1. Never use an ingredient they are allergic to. Never break their diet. Skip foods they won't eat.
        2. If they only want to use what they have, use only those foods plus salt, pepper, oil and water.
        3. Keep the total cooking time at or under their limit. Keep it simple for their skill.
        4. Pick normal, tasty dishes with real names (for example "Chicken and rice bowl", "Greek omelette").
        5. Ingredient amounts are for ONE person's portion. Give each ingredient its grams and its
           nutrition per 100 g (kcal, protein, fat, carbs, fibre, and the vitamins and minerals).
           Mark "have": true for ingredients from their list, false for ones to buy.
        6. Steps: 5 to 10 steps in order, each with "phase" (prep, cook or serve), a short "title" and a
           "text" that says how much (spoons with grams, like "1 tbsp (10 g) oil"), how hot (low, medium or
           high, or °C for the oven), how long, and how to tell it is done. "uses" lists that step's
           ingredients with amounts. Add "minutes" only for waiting steps (simmer, bake, rest).
        7. Write numbers, not words, in the JSON. Use the exact keys shown.
        Example item: $PLAN_ITEM_EXAMPLE

        ${planShape(mealTypes)}
    """.trimIndent()
}
