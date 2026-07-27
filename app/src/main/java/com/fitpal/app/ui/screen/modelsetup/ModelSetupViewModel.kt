package com.fitpal.app.ui.screen.modelsetup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.NutritionDbStatus
import com.fitpal.app.data.NutritionImporter
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.ml.ModelId
import com.fitpal.app.ml.ModelManager
import com.fitpal.app.ml.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val nutritionImporter: NutritionImporter,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val specs: List<ModelManager.Spec> = modelManager.specs
    val statuses: StateFlow<Map<ModelId, ModelStatus>> = modelManager.statuses
    val nutritionStatus: StateFlow<NutritionDbStatus> = nutritionImporter.status
    val brandedStatus: StateFlow<NutritionDbStatus> = nutritionImporter.brandedStatus
    val extraStatus: StateFlow<NutritionDbStatus> = nutritionImporter.extraStatus
    val hfToken: StateFlow<String?> = settingsRepository.hfToken

    init {
        viewModelScope.launch {
            nutritionImporter.refreshStatus()
            nutritionImporter.refreshBrandedStatus()
            nutritionImporter.refreshExtraStatus()
        }
    }

    fun setHfToken(token: String) = settingsRepository.setHfToken(token)

    fun downloadAll() = modelManager.startDownloadAll()

    fun retry(id: ModelId) = modelManager.startDownload(id)

    fun importNutritionDb() = nutritionImporter.startImport()

    fun importBranded() = nutritionImporter.startBrandedImport()

    /** Download popular Central-European foods from Open Food Facts (automatic, one-time). */
    fun downloadEuropeanFoods() = nutritionImporter.startEuropeanDownload()

    /** Import a user-picked CSV of extra foods (e.g. a bigger Open Food Facts slice). */
    fun importExtraFoods(uri: Uri) = nutritionImporter.startCsvImport(uri)
}
