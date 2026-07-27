package com.fitpal.app.data.local

import com.fitpal.app.data.local.entity.UsdaFoodEntity

/**
 * A small built-in food database so Manual Entry and Describe-to-AI work
 * out of the box, before the full USDA database is imported.
 *
 * All values are per 100g. Negative fdcIds mark these as seed entries
 * (real USDA ids are positive), so they won't collide when the real
 * database is imported later.
 *
 * Sources: approximate USDA FoodData Central averages, rounded for readability.
 */
object FoodSeedData {

    val foods: List<UsdaFoodEntity> = listOf(
        //          fdcId  description                       kcal  prot  fat   carb  serving  category
        UsdaFoodEntity(-1,  "Chicken breast (grilled)",      165f, 31f,  3.6f, 0f,    150f, "Protein"),
        UsdaFoodEntity(-2,  "Chicken thigh (cooked)",        209f, 26f,  11f,  0f,    140f, "Protein"),
        UsdaFoodEntity(-3,  "Turkey breast (cooked)",        135f, 30f,  1f,   0f,    140f, "Protein"),
        UsdaFoodEntity(-4,  "Beef, ground (cooked)",         250f, 26f,  15f,  0f,    150f, "Protein"),
        UsdaFoodEntity(-5,  "Pork chop (cooked)",            231f, 26f,  14f,  0f,    150f, "Protein"),
        UsdaFoodEntity(-6,  "Salmon (cooked)",               208f, 20f,  13f,  0f,    140f, "Protein"),
        UsdaFoodEntity(-7,  "Tuna (canned in water)",        116f, 26f,  1f,   0f,    100f, "Protein"),
        UsdaFoodEntity(-8,  "Shrimp (cooked)",               99f,  24f,  0.3f, 0.2f,  100f, "Protein"),
        UsdaFoodEntity(-9,  "Egg (whole)",                   155f, 13f,  11f,  1.1f,  50f,  "Protein"),
        UsdaFoodEntity(-10, "Tofu (firm)",                   76f,  8f,   4.8f, 1.9f,  100f, "Protein"),

        UsdaFoodEntity(-11, "White rice (cooked)",           130f, 2.7f, 0.3f, 28f,   150f, "Grains"),
        UsdaFoodEntity(-12, "Brown rice (cooked)",           123f, 2.7f, 1f,   26f,   150f, "Grains"),
        UsdaFoodEntity(-13, "Fried rice",                    163f, 3f,   5f,   26f,   200f, "Grains"),
        UsdaFoodEntity(-14, "Pasta (cooked)",                158f, 6f,   0.9f, 31f,   180f, "Grains"),
        UsdaFoodEntity(-15, "White bread",                   265f, 9f,   3.2f, 49f,   30f,  "Grains"),
        UsdaFoodEntity(-16, "Whole wheat bread",             247f, 13f,  3.4f, 41f,   30f,  "Grains"),
        UsdaFoodEntity(-17, "Oats (dry)",                    389f, 17f,  7f,   66f,   40f,  "Grains"),
        UsdaFoodEntity(-18, "Quinoa (cooked)",               120f, 4.4f, 1.9f, 21f,   150f, "Grains"),

        UsdaFoodEntity(-19, "Broccoli",                      34f,  2.8f, 0.4f, 7f,    100f, "Vegetables"),
        UsdaFoodEntity(-20, "Carrot",                        41f,  0.9f, 0.2f, 10f,   80f,  "Vegetables"),
        UsdaFoodEntity(-21, "Spinach",                       23f,  2.9f, 0.4f, 3.6f,  60f,  "Vegetables"),
        UsdaFoodEntity(-22, "Tomato",                        18f,  0.9f, 0.2f, 3.9f,  100f, "Vegetables"),
        UsdaFoodEntity(-23, "Cucumber",                      15f,  0.7f, 0.1f, 3.6f,  100f, "Vegetables"),
        UsdaFoodEntity(-24, "Bell pepper",                   31f,  1f,   0.3f, 6f,    100f, "Vegetables"),
        UsdaFoodEntity(-25, "Onion",                         40f,  1.1f, 0.1f, 9f,    50f,  "Vegetables"),
        UsdaFoodEntity(-26, "Mushrooms",                     22f,  3.1f, 0.3f, 3.3f,  70f,  "Vegetables"),
        UsdaFoodEntity(-27, "Green beans",                   31f,  1.8f, 0.2f, 7f,    100f, "Vegetables"),
        UsdaFoodEntity(-28, "Corn",                          86f,  3.2f, 1.2f, 19f,   100f, "Vegetables"),
        UsdaFoodEntity(-29, "Potato (boiled)",               87f,  1.9f, 0.1f, 20f,   150f, "Vegetables"),
        UsdaFoodEntity(-30, "Sweet potato (cooked)",         86f,  1.6f, 0.1f, 20f,   150f, "Vegetables"),

        UsdaFoodEntity(-31, "Banana",                        89f,  1.1f, 0.3f, 23f,   120f, "Fruit"),
        UsdaFoodEntity(-32, "Apple",                         52f,  0.3f, 0.2f, 14f,   150f, "Fruit"),
        UsdaFoodEntity(-33, "Orange",                        47f,  0.9f, 0.1f, 12f,   130f, "Fruit"),
        UsdaFoodEntity(-34, "Strawberries",                  32f,  0.7f, 0.3f, 7.7f,  100f, "Fruit"),
        UsdaFoodEntity(-35, "Blueberries",                   57f,  0.7f, 0.3f, 14f,   100f, "Fruit"),
        UsdaFoodEntity(-36, "Grapes",                        69f,  0.7f, 0.2f, 18f,   100f, "Fruit"),
        UsdaFoodEntity(-37, "Avocado",                       160f, 2f,   15f,  9f,    100f, "Fruit"),

        UsdaFoodEntity(-38, "Milk (whole)",                  61f,  3.2f, 3.3f, 4.8f,  240f, "Dairy"),
        UsdaFoodEntity(-39, "Greek yogurt (plain)",          59f,  10f,  0.4f, 3.6f,  170f, "Dairy"),
        UsdaFoodEntity(-40, "Cheddar cheese",                403f, 25f,  33f,  1.3f,  30f,  "Dairy"),
        UsdaFoodEntity(-41, "Butter",                        717f, 0.9f, 81f,  0.1f,  10f,  "Dairy"),

        UsdaFoodEntity(-42, "Black beans (cooked)",          132f, 8.9f, 0.5f, 24f,   130f, "Legumes"),
        UsdaFoodEntity(-43, "Lentils (cooked)",              116f, 9f,   0.4f, 20f,   130f, "Legumes"),
        UsdaFoodEntity(-44, "Chickpeas (cooked)",            164f, 8.9f, 2.6f, 27f,   130f, "Legumes"),

        UsdaFoodEntity(-45, "Almonds",                       579f, 21f,  50f,  22f,   30f,  "Nuts & Fats"),
        UsdaFoodEntity(-46, "Peanut butter",                 588f, 25f,  50f,  20f,   30f,  "Nuts & Fats"),
        UsdaFoodEntity(-47, "Olive oil",                     884f, 0f,   100f, 0f,    10f,  "Nuts & Fats"),

        UsdaFoodEntity(-48, "Pizza (cheese)",                266f, 11f,  10f,  33f,   120f, "Prepared"),
        UsdaFoodEntity(-49, "Hamburger (plain)",             295f, 17f,  14f,  24f,   150f, "Prepared"),
        UsdaFoodEntity(-50, "French fries",                  312f, 3.4f, 15f,  41f,   120f, "Prepared"),

        UsdaFoodEntity(-51, "Honey",                         304f, 0.3f, 0f,   82f,   20f,  "Other"),
        UsdaFoodEntity(-52, "Sugar (white)",                 387f, 0f,   0f,   100f,  10f,  "Other"),

        // ---- Condiments & sauces ----
        UsdaFoodEntity(-53, "Ketchup", 112f, 1.7f, 0.1f, 26f, 15f, "Condiments"),
        UsdaFoodEntity(-54, "Mayonnaise", 680f, 1f, 75f, 0.6f, 15f, "Condiments"),
        UsdaFoodEntity(-55, "Mustard", 66f, 4f, 4f, 5f, 10f, "Condiments"),
        UsdaFoodEntity(-56, "Soy sauce", 53f, 8f, 0.6f, 4.9f, 15f, "Condiments"),
        UsdaFoodEntity(-57, "BBQ sauce", 172f, 0.8f, 0.6f, 41f, 30f, "Condiments"),
        UsdaFoodEntity(-58, "Sour cream", 198f, 2.4f, 19f, 4.6f, 30f, "Condiments"),
        UsdaFoodEntity(-59, "Hummus", 166f, 8f, 10f, 14f, 30f, "Condiments"),
        UsdaFoodEntity(-60, "Pesto", 450f, 5f, 44f, 6f, 20f, "Condiments"),
        UsdaFoodEntity(-61, "Salsa", 36f, 1.5f, 0.2f, 7f, 30f, "Condiments"),
        UsdaFoodEntity(-62, "Tomato sauce (passata)", 35f, 1.6f, 0.2f, 7f, 100f, "Condiments"),
        UsdaFoodEntity(-63, "Jam", 250f, 0.4f, 0.1f, 62f, 20f, "Condiments"),
        UsdaFoodEntity(-64, "Maple syrup", 260f, 0f, 0.1f, 67f, 20f, "Condiments"),
        UsdaFoodEntity(-65, "Ranch dressing", 430f, 1f, 45f, 6f, 30f, "Condiments"),
        UsdaFoodEntity(-66, "Vinegar", 18f, 0f, 0f, 0.9f, 15f, "Condiments"),
        UsdaFoodEntity(-67, "Chocolate hazelnut spread", 539f, 6f, 31f, 57f, 20f, "Condiments"),

        // ---- More proteins ----
        UsdaFoodEntity(-68, "Bacon (cooked)", 541f, 37f, 42f, 1.4f, 30f, "Protein"),
        UsdaFoodEntity(-69, "Sausage (pork)", 301f, 13f, 27f, 2f, 75f, "Protein"),
        UsdaFoodEntity(-70, "Ham", 145f, 21f, 6f, 1.5f, 50f, "Protein"),
        UsdaFoodEntity(-71, "Cod (cooked)", 105f, 23f, 0.9f, 0f, 140f, "Protein"),
        UsdaFoodEntity(-72, "Sardines (canned)", 208f, 25f, 11f, 0f, 100f, "Protein"),
        UsdaFoodEntity(-73, "Lamb (cooked)", 294f, 25f, 21f, 0f, 150f, "Protein"),
        UsdaFoodEntity(-74, "Egg white", 52f, 11f, 0.2f, 0.7f, 33f, "Protein"),
        UsdaFoodEntity(-75, "Cottage cheese", 98f, 11f, 4.3f, 3.4f, 100f, "Protein"),
        UsdaFoodEntity(-76, "Tempeh", 192f, 20f, 11f, 8f, 100f, "Protein"),

        // ---- More dairy ----
        UsdaFoodEntity(-77, "Skim milk", 34f, 3.4f, 0.1f, 5f, 240f, "Dairy"),
        UsdaFoodEntity(-78, "Mozzarella", 280f, 22f, 17f, 2.2f, 30f, "Dairy"),
        UsdaFoodEntity(-79, "Parmesan", 431f, 38f, 29f, 4.1f, 15f, "Dairy"),
        UsdaFoodEntity(-80, "Feta", 264f, 14f, 21f, 4f, 30f, "Dairy"),
        UsdaFoodEntity(-81, "Cream cheese", 342f, 6f, 34f, 4f, 30f, "Dairy"),
        UsdaFoodEntity(-82, "Yogurt (natural)", 61f, 3.5f, 3.3f, 4.7f, 150f, "Dairy"),
        UsdaFoodEntity(-83, "Kefir", 41f, 3.3f, 0.9f, 4.5f, 200f, "Dairy"),
        UsdaFoodEntity(-84, "Heavy cream", 340f, 2.1f, 36f, 2.8f, 30f, "Dairy"),
        UsdaFoodEntity(-85, "Ice cream (vanilla)", 207f, 3.5f, 11f, 24f, 60f, "Dairy"),

        // ---- More grains & bread ----
        UsdaFoodEntity(-86, "Rye bread", 259f, 8.5f, 3.3f, 48f, 30f, "Grains"),
        UsdaFoodEntity(-87, "Bagel", 250f, 10f, 1.5f, 49f, 80f, "Grains"),
        UsdaFoodEntity(-88, "Tortilla (flour)", 312f, 8f, 8f, 50f, 40f, "Grains"),
        UsdaFoodEntity(-89, "Couscous (cooked)", 112f, 3.8f, 0.2f, 23f, 150f, "Grains"),
        UsdaFoodEntity(-90, "Buckwheat (cooked)", 92f, 3.4f, 0.6f, 20f, 150f, "Grains"),
        UsdaFoodEntity(-91, "Cornflakes", 357f, 7f, 0.4f, 84f, 30f, "Grains"),
        UsdaFoodEntity(-92, "Granola", 471f, 10f, 20f, 64f, 45f, "Grains"),
        UsdaFoodEntity(-93, "Crackers", 502f, 9f, 25f, 61f, 15f, "Grains"),
        UsdaFoodEntity(-94, "Croissant", 406f, 8f, 21f, 46f, 60f, "Grains"),
        UsdaFoodEntity(-95, "Pancakes", 227f, 6f, 9f, 28f, 80f, "Grains"),

        // ---- More vegetables ----
        UsdaFoodEntity(-96, "Cabbage", 25f, 1.3f, 0.1f, 6f, 100f, "Vegetables"),
        UsdaFoodEntity(-97, "Cauliflower", 25f, 1.9f, 0.3f, 5f, 100f, "Vegetables"),
        UsdaFoodEntity(-98, "Zucchini", 17f, 1.2f, 0.3f, 3.1f, 100f, "Vegetables"),
        UsdaFoodEntity(-99, "Eggplant", 25f, 1f, 0.2f, 6f, 100f, "Vegetables"),
        UsdaFoodEntity(-100, "Lettuce", 15f, 1.4f, 0.2f, 2.9f, 50f, "Vegetables"),
        UsdaFoodEntity(-101, "Celery", 16f, 0.7f, 0.2f, 3f, 60f, "Vegetables"),
        UsdaFoodEntity(-102, "Garlic", 149f, 6.4f, 0.5f, 33f, 5f, "Vegetables"),
        UsdaFoodEntity(-103, "Peas", 81f, 5.4f, 0.4f, 14f, 80f, "Vegetables"),
        UsdaFoodEntity(-104, "Asparagus", 20f, 2.2f, 0.1f, 3.9f, 100f, "Vegetables"),
        UsdaFoodEntity(-105, "Beetroot", 43f, 1.6f, 0.2f, 10f, 100f, "Vegetables"),
        UsdaFoodEntity(-106, "Radish", 16f, 0.7f, 0.1f, 3.4f, 50f, "Vegetables"),
        UsdaFoodEntity(-107, "Pumpkin", 26f, 1f, 0.1f, 6.5f, 100f, "Vegetables"),
        UsdaFoodEntity(-108, "Kale", 49f, 4.3f, 0.9f, 9f, 60f, "Vegetables"),
        UsdaFoodEntity(-109, "Green onion", 32f, 1.8f, 0.2f, 7.3f, 20f, "Vegetables"),
        UsdaFoodEntity(-110, "Dill", 43f, 3.5f, 1.1f, 7f, 5f, "Vegetables"),

        // ---- More fruit ----
        UsdaFoodEntity(-111, "Pear", 57f, 0.4f, 0.1f, 15f, 150f, "Fruit"),
        UsdaFoodEntity(-112, "Peach", 39f, 0.9f, 0.3f, 10f, 120f, "Fruit"),
        UsdaFoodEntity(-113, "Watermelon", 30f, 0.6f, 0.2f, 8f, 150f, "Fruit"),
        UsdaFoodEntity(-114, "Pineapple", 50f, 0.5f, 0.1f, 13f, 100f, "Fruit"),
        UsdaFoodEntity(-115, "Mango", 60f, 0.8f, 0.4f, 15f, 120f, "Fruit"),
        UsdaFoodEntity(-116, "Kiwi", 61f, 1.1f, 0.5f, 15f, 70f, "Fruit"),
        UsdaFoodEntity(-117, "Cherries", 50f, 1f, 0.3f, 12f, 100f, "Fruit"),
        UsdaFoodEntity(-118, "Raspberries", 52f, 1.2f, 0.7f, 12f, 100f, "Fruit"),
        UsdaFoodEntity(-119, "Lemon", 29f, 1.1f, 0.3f, 9f, 60f, "Fruit"),
        UsdaFoodEntity(-120, "Plum", 46f, 0.7f, 0.3f, 11f, 70f, "Fruit"),
        UsdaFoodEntity(-121, "Dates", 282f, 2.5f, 0.4f, 75f, 25f, "Fruit"),
        UsdaFoodEntity(-122, "Raisins", 299f, 3.1f, 0.5f, 79f, 30f, "Fruit"),

        // ---- More legumes, nuts & fats ----
        UsdaFoodEntity(-123, "Kidney beans (cooked)", 127f, 8.7f, 0.5f, 23f, 130f, "Legumes"),
        UsdaFoodEntity(-124, "Edamame", 121f, 11f, 5f, 9f, 90f, "Legumes"),
        UsdaFoodEntity(-125, "Walnuts", 654f, 15f, 65f, 14f, 30f, "Nuts & Fats"),
        UsdaFoodEntity(-126, "Cashews", 553f, 18f, 44f, 30f, 30f, "Nuts & Fats"),
        UsdaFoodEntity(-127, "Sunflower seeds", 584f, 21f, 51f, 20f, 30f, "Nuts & Fats"),
        UsdaFoodEntity(-128, "Chia seeds", 486f, 17f, 31f, 42f, 15f, "Nuts & Fats"),
        UsdaFoodEntity(-129, "Coconut oil", 862f, 0f, 100f, 0f, 10f, "Nuts & Fats"),
        UsdaFoodEntity(-130, "Vegetable oil", 884f, 0f, 100f, 0f, 10f, "Nuts & Fats"),

        // ---- Drinks ----
        UsdaFoodEntity(-131, "Water", 0f, 0f, 0f, 0f, 250f, "Drinks"),
        UsdaFoodEntity(-132, "Coffee (black)", 1f, 0.1f, 0f, 0f, 240f, "Drinks"),
        UsdaFoodEntity(-133, "Tea (unsweetened)", 1f, 0f, 0f, 0.3f, 240f, "Drinks"),
        UsdaFoodEntity(-134, "Orange juice", 45f, 0.7f, 0.2f, 10f, 250f, "Drinks"),
        UsdaFoodEntity(-135, "Apple juice", 46f, 0.1f, 0.1f, 11f, 250f, "Drinks"),
        UsdaFoodEntity(-136, "Cola", 42f, 0f, 0f, 11f, 330f, "Drinks"),
        UsdaFoodEntity(-137, "Almond milk (unsweetened)", 13f, 0.4f, 1.1f, 0.3f, 250f, "Drinks"),
        UsdaFoodEntity(-138, "Lemonade", 40f, 0f, 0f, 10f, 250f, "Drinks"),

        // ---- Snacks & sweets ----
        UsdaFoodEntity(-139, "Dark chocolate", 546f, 4.9f, 31f, 61f, 30f, "Snacks"),
        UsdaFoodEntity(-140, "Milk chocolate", 535f, 7.6f, 30f, 59f, 30f, "Snacks"),
        UsdaFoodEntity(-141, "Potato chips", 536f, 7f, 35f, 53f, 30f, "Snacks"),
        UsdaFoodEntity(-142, "Popcorn", 387f, 12f, 4.5f, 78f, 20f, "Snacks"),
        UsdaFoodEntity(-143, "Cookie", 488f, 5f, 24f, 64f, 15f, "Snacks"),
        UsdaFoodEntity(-144, "Doughnut", 452f, 5f, 25f, 51f, 60f, "Snacks"),
        UsdaFoodEntity(-145, "Muffin", 377f, 5f, 18f, 50f, 70f, "Snacks"),
        UsdaFoodEntity(-146, "Pretzels", 380f, 10f, 3f, 80f, 30f, "Snacks"),
        UsdaFoodEntity(-147, "Protein bar", 350f, 30f, 10f, 40f, 50f, "Snacks"),

        // ---- More prepared dishes ----
        UsdaFoodEntity(-148, "Caesar salad", 190f, 6f, 15f, 8f, 200f, "Prepared"),
        UsdaFoodEntity(-149, "Sushi roll", 140f, 5f, 2f, 28f, 100f, "Prepared"),
        UsdaFoodEntity(-150, "Lasagna", 135f, 8f, 6f, 12f, 250f, "Prepared"),
        UsdaFoodEntity(-151, "Mac and cheese", 164f, 6f, 6f, 20f, 200f, "Prepared"),
        UsdaFoodEntity(-152, "Fried chicken", 246f, 19f, 15f, 8f, 150f, "Prepared"),
        UsdaFoodEntity(-153, "Burrito", 206f, 8f, 8f, 26f, 250f, "Prepared"),
        UsdaFoodEntity(-154, "Scrambled eggs", 149f, 10f, 11f, 1.6f, 120f, "Prepared"),
        UsdaFoodEntity(-155, "Omelette", 154f, 11f, 12f, 0.6f, 150f, "Prepared")
    )
}
