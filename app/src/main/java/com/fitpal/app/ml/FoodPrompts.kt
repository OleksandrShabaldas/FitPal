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
        kvass or kefir base). NEVER include alcohol, plain water as a
        separate item, or anything not actually eaten. If the user states a total weight (e.g.
        "650g"), make the item weights sum to it.
        Then add a "variations" array with 4-6 of the MOST COMMON ways people actually make this
        dish differently — alternative bases, proteins, dressings or add-ons they swap in or add
        (for okroshka: sour cream, mayonnaise, kefir base, kvass base, ham, sausage, boiled beef,
        potato, radish, boiled egg). List as MANY genuinely common ones as you can, not just one.
        Each variation is ONE single ingredient object (never a whole dish or a tip) with the SAME
        fields as an item PLUS a "description" that is a SHORT 2-4 word label naming just that one
        swap/add-on — NOT a sentence, NOT advice. e.g.
        {"name":"Sour cream","grams":40,"kcalPer100g":190,"proteinPer100g":2,"fatPer100g":20,"carbsPer100g":3,"fiberPer100g":0,"waterMlPer100":71,"description":"Sour cream"}
        For each item set "waterMlPer100" = real water content per 100 ml/g (water=100, cola≈89,
        juice≈85, milk≈88; fruit/veg 80-96, soup 88-92, cooked grains 65-70, bread 35, cheese 37,
        nuts 4, oil 0). Estimate honestly for EVERY item, foods included.
        Use realistic calorie densities (kcal per 100 g): light soups 25-60, broths 10-30,
        salads/veg 15-120, fruit 30-90, cooked grains/pasta 110-160, lean meat 120-200,
        cheese/fatty/fried 250-450, oils ~880. A bowl of light soup is NOT calorie-dense.
        Reply with ONLY a JSON object in exactly this format and nothing else:
        {"foods":[{"name":"Coca-Cola","isDrink":true,"grams":330,"items":[{"name":"Coca-Cola","grams":330,"kcalPer100g":42,"proteinPer100g":0,"fatPer100g":0,"carbsPer100g":11,"fiberPer100g":0,"waterMlPer100":89,"vitA":0,"vitC":0,"vitD":0,"calcium":0,"iron":0,"potassium":0,"sodium":5,"b12":0,"folate":0,"b6":0,"magnesium":1,"zinc":0,"vitE":0}],"variations":[]}]}
        Use realistic per-100 values and a realistic serving (grams for food, ml for drinks).
        vitA = vitamin A mcg, vitC = vitamin C mg, vitD = vitamin D mcg,
        calcium/iron/potassium/sodium = mg, b12 = vitamin B12 mcg, folate = mcg,
        b6 = vitamin B6 mg, magnesium/zinc = mg, vitE = vitamin E mg. All per 100 g.
    """.trimIndent()

    /** Online text path — leaner than [describe] (the capable model needs less coaxing). */
    fun describeOnline(food: String): String = """
        The user ate or drank: "$food". Return ONLY JSON: {"foods":[ ... ]}.
        - A packaged product or DRINK ("Coca-Cola", "orange juice") = ONE food, "isDrink":true, a
          single item — don't split it into chemicals.
        - A home-cooked / composite dish ("chilli con carne", "okroshka") = ONE food, "isDrink":false,
          whose "items" are its REAL defining ingredients with DIFFERENT realistic weights that sum
          to the dish total. Match a stated total weight. Never add alcohol or plain water.
        - "variations": 4-6 common ways people vary the dish (alt bases/proteins/dressings/add-ons),
          each a SINGLE item object PLUS a "description" that is a SHORT 2-4 word label (e.g.
          "Sour cream", "Kefir base") — never a sentence or a tip. Simple items use [].
        Each item, per 100 g/ml: name, grams (this item's weight), kcalPer100g, proteinPer100g,
        fatPer100g, carbsPer100g, fiberPer100g, waterMlPer100 (water 100, cola 89, juice 85,
        milk 88, fruit/veg 80-96, cooked grains 65-70, bread 35, cheese 37, oil 0), then micros:
        vitA/vitD/b12/folate in mcg; vitC/calcium/iron/potassium/sodium/b6/magnesium/zinc/vitE in mg.
        Realistic calorie densities (light soup 25-60, salad/veg 15-120, cooked grains 110-160,
        fried/fatty 250-450, oil ~880). Never 0 kcal for a real food. Estimate every field honestly.
        Example food: {"name":"Coca-Cola","isDrink":true,"grams":330,"items":[{"name":"Coca-Cola","grams":330,"kcalPer100g":42,"proteinPer100g":0,"fatPer100g":0,"carbsPer100g":11,"fiberPer100g":0,"waterMlPer100":89,"vitA":0,"vitC":0,"vitD":0,"calcium":0,"iron":0,"potassium":0,"sodium":5,"b12":0,"folate":0,"b6":0,"magnesium":1,"zinc":0,"vitE":0}],"variations":[]}
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

            The user's correction: "$instruction".

            Re-evaluate the WHOLE dish with this correction. You may ADD, REMOVE, RENAME or RE-WEIGH
            items so the result is realistic and matches what the user said. Keep the items the user
            did NOT mention. Keep the dish's TOTAL weight about the same ($total $unit) UNLESS the
            correction clearly changes the amount — so if you ADD an item, take weight from the
            others so the items still sum to roughly the total, instead of just piling it on top.

            Reply with ONLY JSON in this exact shape and nothing else:
            {"foods":[{"name":"<dish name>","isDrink":${food.isDrink},"grams":$total,"items":[{"name":"<item>","grams":<weight>,"kcalPer100g":0,"proteinPer100g":0,"fatPer100g":0,"carbsPer100g":0,"fiberPer100g":0,"waterMlPer100":0,"vitA":0,"vitC":0,"vitD":0,"calcium":0,"iron":0,"potassium":0,"sodium":0,"b12":0,"folate":0,"b6":0,"magnesium":0,"zinc":0,"vitE":0}],"variations":[]}]}
            Per 100 $unit for each item: realistic kcalPer100g (light soup 25-60, salad/veg 15-120,
            cooked grains 110-160, lean meat 120-200, cheese/fried 250-450, oil ~880 — never 0 for a
            real food), macros, fiber, waterMlPer100 (water 100, cola 89, juice 85, milk 88,
            fruit/veg 80-96, cooked grains 65-70, bread 35, cheese 37, oil 0), and micros
            (vitA/vitD/b12/folate in mcg; vitC/calcium/iron/potassium/sodium/b6/magnesium/zinc/vitE in mg).
            Estimate every field honestly. Never include alcohol or plain water as an item.
        """.trimIndent()
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
        ALWAYS set "waterMlPer100" to the item's real water content per 100 g/ml: drinks water=100,
        cola=89, juice=85, milk=88; foods — most fruit/veg 80-96 (watermelon 92, cucumber 96,
        orange 86, apple 85), soups 88-92, cooked rice/pasta 65-70, bread 35, cheese 37, nuts 4,
        oil 0. Estimate it honestly for EVERY item, foods included.
        Give REALISTIC per-100 g nutrition for every item - NEVER 0 kcal for a real food, and do
        NOT over-estimate: light soups are 25-60 kcal/100g, broths 10-30, salad/veg 15-120, cooked
        grains 110-160, fried/oily food 250-450. A bowl of light soup is not calorie-dense.
        If a food is a blended or mixed dish whose parts you can't see one-by-one (a soup, stew,
        smoothie or sauce) and you can tell what it is (use the user's note if given), list its
        TYPICAL ingredients as separate items with realistic individual weights instead of one
        opaque entry — e.g. okroshka = potato, cucumber, boiled egg, ham, dill, kvass or kefir.
        Never include alcohol or plain water as an item.
        Reply with ONLY a compact JSON object and no other text. Follow this example EXACTLY in
        shape, but replace the foods and numbers with the real ones you see (these numbers are a
        realistic crepes-with-toppings example):
        {"foods":[
        {"name":"Crepe","isDrink":false,"grams":120,"items":[{"name":"Crepe","grams":120,"kcalPer100g":220,"proteinPer100g":6,"fatPer100g":8,"carbsPer100g":31,"fiberPer100g":1,"waterMlPer100":0,"vitA":30,"vitC":0,"vitD":1,"calcium":80,"iron":1,"potassium":120,"sodium":150,"b12":0.3,"folate":10,"b6":0.05,"magnesium":12,"zinc":0.5,"vitE":0.4}]},
        {"name":"Whipped cream","isDrink":false,"grams":40,"items":[{"name":"Whipped cream","grams":40,"kcalPer100g":340,"proteinPer100g":2,"fatPer100g":36,"carbsPer100g":3,"fiberPer100g":0,"waterMlPer100":0,"vitA":110,"vitC":0,"vitD":0,"calcium":65,"iron":0,"potassium":75,"sodium":30,"b12":0.2,"folate":3,"b6":0.02,"magnesium":7,"zinc":0.2,"vitE":0.5}]},
        {"name":"Caramel sauce","isDrink":false,"grams":25,"items":[{"name":"Caramel sauce","grams":25,"kcalPer100g":310,"proteinPer100g":1,"fatPer100g":9,"carbsPer100g":57,"fiberPer100g":0,"waterMlPer100":0,"vitA":0,"vitC":0,"vitD":0,"calcium":40,"iron":0,"potassium":80,"sodium":200,"b12":0,"folate":0,"b6":0,"magnesium":5,"zinc":0.1,"vitE":0}]},
        {"name":"Strawberries","isDrink":false,"grams":50,"items":[{"name":"Strawberries","grams":50,"kcalPer100g":33,"proteinPer100g":1,"fatPer100g":0,"carbsPer100g":8,"fiberPer100g":2,"waterMlPer100":91,"vitA":1,"vitC":59,"vitD":0,"calcium":16,"iron":0,"potassium":150,"sodium":1,"b12":0,"folate":24,"b6":0.05,"magnesium":13,"zinc":0.1,"vitE":0.3}]}
        ]}
        Units per 100 g: vitA/vitD/b12/folate = mcg; vitC/calcium/iron/potassium/sodium/b6/magnesium/zinc/vitE = mg.
        Estimate every field honestly; use 0 only when truly absent.
    """.trimIndent()
    }

    /**
     * Online vision prompt — purpose-built for a capable cloud model, so it's leaner than the
     * on-device [vision] prompt (no repeated coaxing / four worked examples). In ONE pass it reads
     * the plate, names composite dishes FROM THE PHOTO, suggests variations, and — only when the
     * whole plate is a single dish it's unsure about — offers 2-3 candidate dishes to pick from.
     */
    fun visionOnline(note: String): String {
        val noteLine = if (note.isBlank()) "" else
            "User context (trust it): \"$note\".\n\n"
        return noteLine + """
        Identify every distinct food and drink in this photo, including sauces, creams, syrups,
        toppings and garnishes. Describe only what's visibly present. Reply with ONLY this JSON:
        {"foods":[...],"dishCandidates":[...]}

        Each food: {"name","isDrink","grams","items":[ item objects ],"variations":[]}.
        - Name composite/cooked dishes by their REAL name ("Okroshka", "Caesar salad", "Pad Thai")
          with their true ingredients in "items"; list separate plate items as their own foods.
        - "variations": 4-6 common ways people vary that dish (alt dressings, bases, proteins,
          add-ons), each a SINGLE item object PLUS a "description" that is a SHORT 2-4 word label
          (e.g. "Ranch dressing", "Extra chicken") — never a sentence or a tip. Simple items
          (apple, soda) use [].
        - Mark drinks "isDrink":true (ml); solids false (g). Use any fork/coin/bank card in shot for
          SCALE (a bank card is 8.6 cm wide) to judge real portions.

        Each item object, all per 100 g/ml: name, grams (this item's own weight), kcalPer100g,
        proteinPer100g, fatPer100g, carbsPer100g, fiberPer100g, waterMlPer100 (water 100, cola 89,
        juice 85, milk 88, most fruit/veg 80-96, soup 88-92, cooked grains 65-70, bread 35,
        cheese 37, nuts 4, oil 0), then micros: vitA/vitD/b12/folate in mcg; and
        vitC/calcium/iron/potassium/sodium/b6/magnesium/zinc/vitE in mg.
        Realistic calorie densities: light soup 25-60, broth 10-30, salad/veg 15-120, fruit 30-90,
        cooked grains/pasta 110-160, lean meat 120-200, cheese/fried 250-450, oil ~880. Never 0 kcal
        for a real food; never include alcohol or plain water as an item. Estimate every field honestly.
        Example item: {"name":"Crepe","grams":120,"kcalPer100g":220,"proteinPer100g":6,"fatPer100g":8,"carbsPer100g":31,"fiberPer100g":1,"waterMlPer100":0,"vitA":30,"vitC":0,"vitD":1,"calcium":80,"iron":1,"potassium":120,"sodium":150,"b12":0.3,"folate":10,"b6":0.05,"magnesium":12,"zinc":0.5,"vitE":0.4}

        "dishCandidates": include ONLY when the whole plate is a single dish you're genuinely unsure
        about — 2-3 most likely dish names, each {"name","variations":[ item objects with description ]}.
        Use [] when it's clearly one dish, or several separate foods.
    """.trimIndent()
    }

    /** Second pass: name the dish from its loose ingredients (and photo, if available). */
    fun dish(ingredients: List<Ingredient>, note: String): String {
        val names = ingredients.joinToString(", ") { it.name }
        val noteLine = if (note.isBlank()) "" else
            "\n            The user added this context — use it: \"$note\"."
        return """
            Look at THIS photo. These items were found on ONE plate: $names.
            Use BOTH the photo and this list to name the actual dish — the 1-3 most likely
            dishes, most likely first (e.g. a round base with dough, cheese, ham and mushrooms = Pizza).$noteLine
            For EACH dish, list its typical parts that are MISSING from the list above as "missing".
            ALWAYS put the dish's signature sauce or dressing FIRST if it isn't already present
            (for example: Caesar salad -> Caesar dressing; Greek salad -> olive oil and feta;
            pancakes -> maple syrup; fries -> ketchup). Then list 4-6 MORE of the most common
            variations people use — different sauces, proteins, bases or add-ons they swap in
            (e.g. okroshka: sour cream, mayonnaise, kefir, kvass, ham, sausage, beef, potato,
            radish). Include as MANY genuinely common ones as you can, not just one or two.
            Do NOT suggest variants or duplicates of ingredients already in the
            list. NEVER suggest alcohol, plain water, or anything not actually eaten.
            Give each a realistic per-100 nutrition (light soups 25-60 kcal/100g, oils ~880) and a
            small serving size in grams.
            Reply with ONLY a JSON object in exactly this format and nothing else:
            {"dishes":[{"name":"<dish name>","missing":[{"name":"<sauce or dressing>","grams":30,"kcalPer100g":300,"proteinPer100g":1,"fatPer100g":32,"carbsPer100g":4,"fiberPer100g":0,"vitA":0,"vitC":0,"vitD":0,"calcium":10,"iron":0,"potassium":5,"sodium":200,"b12":0,"folate":2,"b6":0,"magnesium":3,"zinc":0,"vitE":1}]}]}
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
            Answer these five questions in flowing prose. They are your INTERNAL structure — do NOT
            print them as headings or a numbered list.

            1. WHAT HAPPENED? Two sentences at most — the user can already see the numbers, so just
               frame the day, don't recite every macro.
            2. IS IT UNUSUAL? Compare today to the 7-day averages in NUTRITION ANALYTICS above. If no
               analytics are given (not enough history), skip this question entirely.
            3. DOES IT MATTER? Put it in weekly context. One high day inside a consistent week is fine
               — say so plainly. A single low-protein day when protein is trending up is noise, not a
               problem. Do NOT dramatize one day.
            4. WHY DID IT HAPPEN? Use the meal contexts (home/restaurant/…), the foods, and the meal
               timing to hypothesise. If the user answered a check-in question above, use that.
            5. WHAT'S THE SMALLEST USEFUL ACTION? One concrete strategy for tomorrow — or, if the day
               and the week both look fine, say plainly that nothing needs changing. Never invent a
               problem to have something to say.
            """.trimIndent()
        } else {
            """
            Answer these five questions in flowing prose. They are your INTERNAL structure — do NOT
            print them as headings or a numbered list.

            1. WHAT HAPPENED THIS $period? Three sentences at most — the overall trajectory, not a
               day-by-day recap.
            2. IS THIS TYPICAL? Compare to the 30-day averages if given, and to the weekday-vs-weekend
               split. Call out only genuine deviations from this user's established pattern.
            3. DOES IT MATTER? Read the trends. Compare the weight trend to the calorie trend — if
               weight isn't moving despite a steady deficit, the target may be off (or logging is
               under-counting); say so. If everything is on track, say that clearly.
            4. WHAT PATTERNS EMERGE? Recurring foods, meal timing, protein distribution across meals,
               weekend behaviour. Mention only patterns the data actually supports.
            5. WHAT'S THE ONE THING TO CHANGE? One high-leverage strategy for next $period — or
               "stay the course" if things are working. If several things are off, pick the single
               most important one; don't hand them a checklist.
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

            TONE & RULES:
            - You are a calm, knowledgeable friend who knows nutrition — NOT a fitness influencer, a
              robot, or a therapist.
            - NEVER use these words/phrases: "fat-bomb", "solid win", "brilliant move", "treat
              yourself", "cheat day", "cheat meal", "guilty pleasure", "naughty", "clean eating",
              "be patient with yourself", "crushing it".
            - Be emotionally neutral about food — no moral judgments. A calorie-dense meal is just
              calorie-dense, not "bad".
            - If a metric is off today but the weekly average is fine, say that clearly. One day is
              not a trend.
            - Recommend STRATEGIES, not branded products or exact clock-times (say "a protein-rich
              snack earlier in the day", not "eat brand-X curd at 6pm").
            - If nothing needs changing, say so and stop. Do not manufacture a problem.
            - Judge intake NET of the ACTIVITY & CALORIES BURNED above — credit training days.
            - Read weight change against training: weight lost during a stretch with little or no
              exercise (especially resistance training) may be muscle, not just fat — say so. Weight
              holding steady on a real deficit while training hard can be recomposition (fat down,
              muscle up), not failure. Use the activity data to judge whether the calorie target fits.
            - A deficit that ISN'T showing on the scale is usually not failure: day-to-day water and
              glycogen swings (±0.5–1 kg) hide slow fat loss over a few weeks, and the "burned" figure
              is a formula estimate that often runs high while food logging runs low — so the true gap
              is smaller than it looks. Frame it that way; don't tell them to just eat even less.
            - Respect the PERSONAL CONTEXT / LIMITATIONS above; never suggest what it rules out.
            - Keep advice realistic and SUSTAINABLE — never a monotonous or extreme diet.
            - ${if (isDaily) "Under 200 words." else "Under 350 words."} Short paragraphs, no bullet lists, no headings.
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
            Work through these five questions internally, then answer in flowing prose (no headings,
            no numbered list):
            1. What happened? (≤2 sentences — just frame the day; the user sees the numbers.)
            2. Is it unusual? (Compare to the 7-day averages in NUTRITION ANALYTICS. Skip if absent.)
            3. Does it matter? (Weekly context — one high day in a steady week is fine; say so. Don't dramatize a single day.)
            4. Why did it happen? (Use meal contexts, foods, timing, and any check-in answer above.)
            5. Smallest useful action? (ONE concrete strategy for tomorrow — or say plainly that nothing needs changing.)
            """.trimIndent()
        else
            """
            Work through these five questions internally, then answer in flowing prose (no headings,
            no numbered list):
            1. What happened this $period? (≤3 sentences — trajectory, not a day-by-day recap.)
            2. Is this typical? (Compare to 30-day averages and the weekday/weekend split; flag only real deviations.)
            3. Does it matter? (Read the trends. Weight trend vs calorie trend — if weight isn't moving on a steady deficit, the target may be off or logging under-counts. If on track, say so.)
            4. What patterns emerge? (Recurring foods, meal timing, protein distribution, weekend behaviour — only what the data supports.)
            5. The one thing to change? (One high-leverage strategy for next $period, or "stay the course".)
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

            Cite the real numbers and foods above; never generic "stick to your goal" advice. Judge
            intake NET of the activity/calories burned (credit training days). Read weight change
            against training: weight lost with little/no exercise (especially resistance training) may
            be muscle, not just fat — flag it; steady weight on a real deficit while training hard can
            be recomposition, not failure; use the activity data to judge whether the target fits. A
            deficit that isn't moving the scale is usually not failure either: water/glycogen swings
            (±0.5–1 kg) hide slow fat loss week to week, and "burned" is a formula estimate that can
            run high while logging runs low — so the real gap is smaller than it looks, not a reason to
            eat even less. Respect the personal context/limitations (never suggest what it rules out). Keep advice
            realistic and SUSTAINABLE. Recommend STRATEGIES, not branded products or exact clock-times.

            TONE: a calm, knowledgeable friend — not a fitness influencer, robot, or therapist. Be
            emotionally neutral about food (no moral judgments; calorie-dense ≠ "bad"). NEVER use:
            "fat-bomb", "solid win", "brilliant move", "treat yourself", "cheat day/meal", "guilty
            pleasure", "clean eating", "be patient with yourself". If nothing needs changing, say so
            and stop — don't manufacture problems.
            ${if (isDaily) "Under 200 words." else "Under 350 words."} Short paragraphs, no bullet lists.
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
     * Reads what's *left* in the day's budget, so it never nags about a big meal there's room for.
     */
    fun mealCoachingTip(
        mealFoods: String,
        todayBeforeMeal: String,
        remainingTargets: String,
        userGoal: String
    ): String = """
        A user just logged this meal. Give ONE short coaching tip about THIS meal, or nothing.
        MEAL (as eaten): $mealFoods
        TODAY BEFORE THIS MEAL: $todayBeforeMeal
        REMAINING TODAY (target minus what's already eaten): $remainingTargets
        GOAL: $userGoal

        Reply with ONLY a JSON object, or the single word null if the meal is fine as-is:
        {"type":"<portion|swap|addition|timing|goal|balance>","message":"<one sentence>"}

        Rules:
        - Return null unless the tip is genuinely useful — a balanced meal that fits the day gets no tip.
        - Be specific to THIS meal and culturally sensible (never "add a salad to pelmeni/pasta/sushi").
        - Weigh the REMAINING budget: if there's plenty of room, don't warn about portion or calories.
        - Recommend a STRATEGY, not a branded product or an exact clock-time.
        - Neutral tone, no moral words ("bad", "cheat", "guilty"). One sentence, under 25 words.
        - type: portion=too big for the budget, swap=replace a component, addition=round it out,
          timing=when to eat it, goal=how it serves their goal, balance=macro/plate balance.
    """.trimIndent()

    /**
     * ONE check-in question the coach asks before a review, when a day/period looks unusual — with
     * concrete, data-specific answer chips. Returns JSON so the screen can render tappable options.
     */
    fun contextQuestion(briefSummary: String, period: String): String = """
        Here is a user's recent nutrition picture:
        $briefSummary

        Ask ONE short question that would help you explain an UNUSUAL pattern in this $period — the
        kind of context the numbers can't show (a family dinner, a stressful stretch, a skipped meal,
        travel, illness). Then offer concrete answer options tailored to THIS data.

        Reply with ONLY this JSON:
        {"question":"<the question, under 15 words>","options":["<opt1>","<opt2>","<opt3>","<opt4>"]}

        Rules:
        - The question must be specific to THIS data — never a generic wellness question.
        - 3 to 5 options, each a concrete, plausible explanation. No "Other" or "I don't know".
        - Keep each option under 5 words. Neutral, non-judgmental wording.
    """.trimIndent()
}
