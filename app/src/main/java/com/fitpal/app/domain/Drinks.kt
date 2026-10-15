package com.fitpal.app.domain

/**
 * Is this food a drink? — so a logged Coca-Cola or juice is measured in ml and counts toward
 * hydration, gets drink presets, and can be let through by the fasting drink rule.
 *
 * The AI says so itself for photo/describe results; this is the shared fallback for everything that
 * only has a name (database and barcode foods, a model that left the flag out). Whole-word matching
 * ([TextMatch]) — the old substring check turned chocolate into cola, steak into tea and watermelon
 * into water. A few food phrases that contain a drink word are ruled out first.
 */
object Drinks {
    /** Food names that contain a drink word but aren't drinks. Checked first. */
    private val notDrinks = listOf(
        "chocolate", "milk chocolate", "chocolate spread", "coffee cake", "tea cake", "teacake", "tea loaf",
        "watermelon", "watercress", "water chestnut", "in water", "in brine", "baking soda", "soda bread",
        "cocoa powder", "cocoa nibs", "nectarine", "vinegar", "wine sauce", "red wine sauce", "beer-battered",
        "beer batter", "rum cake", "rum baba", "milk bread", "condensed milk", "powdered milk", "milk powder",
        "dry milk", "milk roll", "jelly", "ice cream", "ice lolly", "popsicle", "sorbet", "yogurt", "yoghurt",
        "cheese", "porridge", "oatmeal", "cereal", "pudding", "sauce", "soup", "broth powder", "stock cube",
        "jam", "gummy", "gummies", "candy", "cookie", "biscuit", "cake", "pie", "tart", "muffin", "monster munch"
    )

    /** Things you drink. Brands and Central-European / Ukrainian names included. */
    private val drinkWords = listOf(
        // water
        "water", "sparkling water", "mineral water", "minerálka", "minerálna voda", "minerální voda", "voda",
        "вода", "мінеральна вода", "rajec", "bonaqua", "mattoni", "evian", "vittel", "borjomi", "san pellegrino",
        // coffee
        "coffee", "káv*", "kava", "кава", "кофе", "espresso", "ristretto", "doppio", "americano", "long black",
        "latte", "cappuccino", "flat white", "cortado", "macchiato", "mocha", "frappuccino", "frappe", "frappé",
        "cold brew", "iced coffee", "café", "cafe au lait",
        // tea
        "tea", "čaj", "чай", "chai", "matcha latte", "iced tea", "ice tea", "kombucha", "yerba mate", "mate",
        "club-mate", "rooibos", "earl grey",
        // soft & energy drinks
        "cola", "coca-cola", "coke", "pepsi", "kofola", "fanta", "sprite", "7up", "7-up", "mountain dew",
        "dr pepper", "ginger ale", "ginger beer", "tonic", "soda", "lemonade", "limonáda", "limonada",
        "лимонад", "soft drink", "energy drink", "red bull", "monster", "rockstar", "celsius", "burn",
        "hell energy", "big shock", "semtex", "gatorade", "powerade", "isostar", "prime hydration",
        "sports drink", "енергетик",
        // dairy & plant drinks, shakes
        "milk", "mlieko", "mléko", "mleko", "молоко", "kefir", "kefír", "кефір", "кефир", "ayran", "lassi",
        "buttermilk", "acidko", "ryazhenka", "ряжанка", "oat milk", "almond milk", "soy milk", "soya milk",
        "rice milk", "coconut milk drink", "milkshake*", "shake", "protein shake", "smoothie", "hot chocolate",
        "chocolate milk", "cocoa", "kakao", "какао",
        // juices & traditional drinks
        "juice", "džús", "dzus", "šťáva", "sok", "сік", "сок", "nectar", "kompot", "компот", "uzvar",
        "узвар", "mors", "морс", "kvass", "kvas", "квас", "kissel", "кисіль",
        // alcohol
        "beer", "pivo", "пиво", "lager", "ale", "ipa", "stout", "pilsner", "radler", "cider", "wine", "víno",
        "vino", "вино", "prosecco", "champagne", "cava", "spritz", "aperol", "vodka", "горілка", "horilka",
        "whisky", "whiskey", "rum", "gin", "gin and tonic", "tequila", "liqueur", "slivovice", "slivovica",
        "becherovka", "fernet", "mojito", "margarita", "negroni", "cocktail", "mulled wine", "svařák", "grog",
        // generic
        "drink", "beverage", "nápoj", "напій", "напиток"
    )

    /** Names that are a drink outright — enough to overrule a model that said "isDrink": false. */
    private val strongDrinks = listOf(
        "water", "coffee", "espresso", "americano", "latte", "cappuccino", "flat white", "tea", "juice",
        "coca-cola", "coke", "pepsi", "kofola", "fanta", "sprite", "red bull", "monster", "beer", "wine",
        "milkshake*", "smoothie", "protein shake", "kombucha", "kefir", "lemonade", "energy drink",
        "kava", "káv*", "čaj", "чай", "кава", "пиво", "вино", "сік", "сок", "квас"
    )

    /** Heuristic: is this food likely a drink (measured in ml, counts toward water)? */
    fun isDrink(name: String, category: String?): Boolean {
        if (category != null && TextMatch.containsAny(category, listOf("drink", "beverage", "drinks", "beverages"))) return true
        // USDA's own naming: "Beverages, coffee, brewed …".
        if (name.trim().startsWith("beverages", ignoreCase = true)) return true
        if (TextMatch.containsAny(name, notDrinks) && !TextMatch.containsAny(name, strongPhrases)) return false
        return TextMatch.containsAny(name, drinkWords)
    }

    /** True when the name alone leaves no doubt it's a drink (a model's explicit "false" loses to it). */
    fun isDefinitelyDrink(name: String): Boolean =
        TextMatch.containsAny(name, strongDrinks) &&
            (!TextMatch.containsAny(name, notDrinks) || TextMatch.containsAny(name, strongPhrases))

    /** Drink phrases that contain a "not a drink" word but are drinks after all. */
    private val strongPhrases = listOf("hot chocolate", "chocolate milk", "chocolate shake", "chocolate milkshake", "milkshake*")

    /** Water per 100 ml for a drink, estimated from its macros (most of a drink is water). */
    fun estimateWaterPer100(carbs: Float, protein: Float, fat: Float): Float {
        val solids = (carbs + protein + fat).coerceAtLeast(0f)
        return (100f - solids).coerceIn(50f, 100f)
    }
}
