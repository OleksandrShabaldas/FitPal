package com.fitpal.app.domain

/**
 * Ready-made sauces, dressings, dips and spreads: things that are logged **as themselves**
 * ("Tartar sauce, 30 g"), never broken into their recipe (mayonnaise + pickles + capers). The AI is
 * told this in every food prompt; [com.fitpal.app.ml.FoodJsonParser] uses this list to repair a reply
 * that broke one up anyway.
 *
 * A name counts only when it *is* the condiment, give or take a qualifier ("Spicy mayo", "Side of
 * ketchup", "Homemade pesto") — a dish that merely contains one ("Honey garlic chicken", "Pesto pasta")
 * is a dish. English plus the Czech / Slovak / Ukrainian names the app's users write. Bare "tatar" is
 * left out on purpose: in Czech "tatarák" is a beef dish, not a sauce.
 */
object Condiments {
    private val terms: Set<String> = setOf(
        // mayonnaise-based
        "tartar sauce", "tartare sauce", "tatar sauce", "tatarská omáčka", "tatárska omáčka", "соус тартар",
        "тартар соус", "mayonnaise", "mayo", "majonéza", "majonéz", "майонез", "aioli", "remoulade",
        "garlic sauce", "garlic mayo", "česneková omáčka", "cocktail sauce", "thousand island",
        // tomato / chilli
        "ketchup", "kečup", "кетчуп", "bbq sauce", "barbecue sauce", "hot sauce", "sriracha", "salsa",
        "sweet chili sauce", "sweet chilli sauce", "chilli sauce", "chili sauce", "tabasco",
        // mustard
        "mustard", "hořčice", "horčica", "гірчиця", "горчица", "honey mustard", "dijon mustard",
        // dressings
        "ranch dressing", "ranch", "caesar dressing", "vinaigrette", "blue cheese dressing", "salad dressing",
        "french dressing", "italian dressing", "yogurt dressing", "yoghurt dressing",
        // dips & spreads
        "pesto", "hummus", "guacamole", "tzatziki", "tahini", "baba ganoush", "chutney", "relish",
        "peanut butter", "nutella", "jam", "marmalade", "džem", "marmeláda", "варення", "honey",
        // Asian sauces
        "soy sauce", "sójová omáčka", "teriyaki sauce", "hoisin sauce", "oyster sauce", "fish sauce",
        "sweet and sour sauce", "curry sauce", "satay sauce", "peanut sauce",
        // hot & dessert sauces
        "gravy", "hollandaise", "hollandaise sauce", "béarnaise", "bearnaise", "béarnaise sauce",
        "caramel sauce", "chocolate sauce", "maple syrup", "whipped cream", "šlehačka"
    )

    /** Words that describe a condiment without making it a dish ("spicy", "side of", "homemade"). */
    private val qualifiers = listOf(
        "homemade", "home made", "domácí", "domáca", "домашній", "fresh", "classic", "creamy", "light", "lite",
        "reduced fat", "low fat", "spicy", "extra", "small", "large", "portion of", "portion", "side of",
        "on the side", "side", "pot of", "pot", "ramekin of", "ramekin", "dip", "a", "of", "with"
    )

    private val qualifierRegexes: List<Regex> = qualifiers.map {
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }

    /** True when [name] names a ready-made condiment on its own, not a dish that contains one. */
    fun isCondiment(name: String): Boolean {
        var n = name.lowercase().replace(Regex("[()\\[\\],.:;!?\"']"), " ")
        for (q in qualifierRegexes) n = q.replace(n, " ")
        n = n.trim().replace(Regex("\\s+"), " ")
        return n in terms
    }
}
