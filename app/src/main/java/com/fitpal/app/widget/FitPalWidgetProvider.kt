package com.fitpal.app.widget

/**
 * The Quick log widget — Snap / Describe / Search / Add food, with how much is left today when it's
 * tall enough. Keeps its original class name so widgets placed before the widget family existed keep
 * working (the launcher knows them by this name); it now draws like the rest ([WidgetKind.QUICK_LOG]).
 */
class FitPalWidgetProvider : FitPalAppWidget(WidgetKind.QUICK_LOG)
