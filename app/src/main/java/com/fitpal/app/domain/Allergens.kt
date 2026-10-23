package com.fitpal.app.domain

/**
 * The allergen check (docs/redesign/10): every planned meal's ingredients are matched, in code, against
 * the user's allergies — the prompt says "never", but a model can still slip, and this is the one
 * place a slip mustn't get through. Whole words only ([TextMatch]), with the common foods each
 * allergen hides in ("pesto" → tree nuts, "tahini" → sesame).
 */
object Allergens {

    private val LEXICON: Map<String, List<String>> = mapOf(
        "peanuts" to listOf("peanut*", "groundnut*", "satay", "monkey nut*"),
        "tree nuts" to listOf(
            "almond*", "walnut*", "cashew*", "pecan*", "pistachio*", "hazelnut*", "macadamia*", "brazil nut*",
            "pine nut*", "praline", "marzipan", "frangipane", "nutella", "pesto", "gianduja", "nut butter", "mixed nuts", "nuts"
        ),
        "gluten" to listOf(
            "wheat", "flour", "bread*", "pasta", "spaghetti", "penne", "noodle*", "couscous", "bulgur", "barley", "rye",
            "semolina", "seitan", "spelt", "tortilla*", "pita", "naan", "breadcrumb*", "panko", "croissant*", "bagel*",
            "cracker*", "biscuit*", "cake", "pastry", "pizza", "soy sauce", "beer", "farro", "orzo"
        ),
        "lactose" to listOf(
            "milk", "cheese", "cream", "butter", "yogurt", "yoghurt", "kefir", "ghee", "whey", "ricotta", "mozzarella",
            "parmesan", "cheddar", "feta", "mascarpone", "paneer", "custard", "ice cream", "sour cream", "crème fraîche",
            "creme fraiche", "halloumi", "brie", "camembert", "cottage cheese", "quark", "tvaroh", "smetana"
        ),
        "eggs" to listOf("egg*", "mayonnaise", "mayo", "aioli", "meringue", "omelette", "omelet", "frittata", "hollandaise"),
        "soy" to listOf("soy*", "soya", "tofu", "tempeh", "edamame", "miso", "soy sauce", "tamari"),
        "fish" to listOf(
            "fish", "salmon", "tuna", "cod", "haddock", "trout", "mackerel", "sardine*", "anchov*", "herring", "tilapia",
            "sea bass", "halibut", "pollock", "fish sauce", "worcestershire"
        ),
        "shellfish" to listOf("shrimp*", "prawn*", "crab*", "lobster*", "mussel*", "clam*", "oyster*", "scallop*", "squid", "calamari", "octopus"),
        "sesame" to listOf("sesame", "tahini", "hummus", "halva", "halvah", "za'atar", "zaatar", "gomashio")
    )

    /**
     * Names that look like an allergen but aren't one — an ingredient matching one of these is
     * skipped for that allergen ("almond milk" has no lactose, "rice noodles" no gluten).
     */
    private val EXCEPTIONS: Map<String, List<String>> = mapOf(
        "lactose" to listOf(
            "almond milk", "oat milk", "soy milk", "soya milk", "coconut milk", "coconut cream", "rice milk", "cashew milk",
            "peanut butter", "nut butter", "almond butter", "cashew butter", "cocoa butter", "apple butter", "vegan cheese",
            "cream of tartar", "lactose-free", "lactose free", "dairy-free", "dairy free"
        ),
        "gluten" to listOf("rice noodle*", "glass noodle*", "gluten-free", "gluten free", "rice flour", "almond flour", "corn tortilla*", "buckwheat"),
        "eggs" to listOf("eggplant*", "egg-free", "egg free", "vegan mayo*"),
        "tree nuts" to listOf("nutmeg", "coconut*", "butternut*", "water chestnut*"),
        "fish" to listOf("fish-free")
    )

    /**
     * The user's [allergies] found in [ingredients] — e.g. {"Tree nuts"} for a pesto. An allergy that
     * isn't in the lexicon (typed by hand: "kiwi") matches by its own name.
     */
    fun found(ingredients: List<String>, allergies: Set<String>): Set<String> =
        allergies.filterTo(LinkedHashSet()) { allergy ->
            val key = allergy.lowercase().trim()
            val words = LEXICON[key] ?: listOf(key)
            val notIt = EXCEPTIONS[key].orEmpty()
            ingredients.any { TextMatch.containsAny(it, words) && !TextMatch.containsAny(it, notIt) }
        }
}
