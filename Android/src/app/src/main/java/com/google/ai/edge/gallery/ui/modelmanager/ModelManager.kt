/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.modelmanager

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.preview.PreviewModelManagerViewModel
// import com.google.ai.edge.gallery.ui.preview.TASK_TEST1
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api

import android.util.Log
import androidx.compose.ui.platform.LocalContext
import com.google.ai.edge.gallery.proto.ImportedModel
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.google.ai.edge.gallery.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.data.AppBarAction
import com.google.ai.edge.gallery.data.AppBarActionType
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task

/** A screen to manage models. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManager(
  task: Task,
  viewModel: ModelManagerViewModel,
  enableAnimation: Boolean,
  navigateUp: () -> Unit,
  onModelClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
  onBenchmarkClicked: (Model) -> Unit = {},
) {
  // Set title based on the task.
  val title = task.label
  // Model count.
  val modelCount by remember {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) {
        task.models.size
      } else {
        -1
      }
    }
  }

  // Navigate up when there are no models left.
  LaunchedEffect(modelCount) {
    if (modelCount == 0) {
      navigateUp()
    }
  }

  // Handle system's edge swipe.
  BackHandler { navigateUp() }


  var showUnsupportedFileTypeDialog by remember { mutableStateOf(false) }
  var showUnsupportedWebModelDialog by remember { mutableStateOf(false) }
  val selectedLocalModelFileUri = remember { mutableStateOf<Uri?>(null) }
  val selectedImportedModelInfo = remember { mutableStateOf<ImportedModel?>(null) }
  var showImportDialog by remember { mutableStateOf(false) }
  var showImportingDialog by remember { mutableStateOf(false) }
  val context = LocalContext.current

  var showImportModelSheet by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val filePickerLauncher: ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          val fileName = getFileName(context = context, uri = uri)
          // Show warning for model file types other than .task and .litertlm.
          if (fileName != null && !fileName.endsWith(".task") && !fileName.endsWith(".litertlm")) {
            showUnsupportedFileTypeDialog = true
          }
          // Show warning for web-only model (by checking if the file name has "-web" in it).
          else if (fileName != null && fileName.lowercase().contains("-web")) {
            showUnsupportedWebModelDialog = true
          } else {
            selectedLocalModelFileUri.value = uri
            showImportDialog = true
          }
        }
      }
    }

  Scaffold(
    modifier = modifier,
    topBar = {
      GalleryTopAppBar(
        title = title,
        leftAction = AppBarAction(actionType = AppBarActionType.NAVIGATE_UP, actionFn = navigateUp),
      )
    },
    floatingActionButton = {
      val cdImportModelFab = stringResource(R.string.cd_import_model_button)
      SmallFloatingActionButton(
        onClick = { showImportModelSheet = true },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.semantics { contentDescription = cdImportModelFab },
      ) {
        Icon(Icons.Filled.Add, contentDescription = null)
      }
    },
  ) { innerPadding ->

    ModelList(
      task = task,
      modelManagerViewModel = viewModel,
      contentPadding = innerPadding,
      enableAnimation = enableAnimation,
      onModelClicked = onModelClicked,
      onBenchmarkClicked = onBenchmarkClicked,
      modifier = Modifier.fillMaxSize(),
    )
  }

  // Import model bottom sheet.
  if (showImportModelSheet) {
    ModalBottomSheet(onDismissRequest = { showImportModelSheet = false }, sheetState = sheetState) {
      Text(
        "Import model",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
      )
      val cbImportFromLocalFile = stringResource(R.string.cd_import_model_from_local_file_button)
      Box(
        modifier =
          Modifier.clickable {
              scope.launch {
                // Give it sometime to show the click effect.
                delay(200)
                showImportModelSheet = false

                // Show file picker.
                val intent =
                  Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    // Single select.
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                  }
                filePickerLauncher.launch(intent)
              }
            }
            .semantics {
              role = Role.Button
              contentDescription = cbImportFromLocalFile
            }
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
          Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
          Text("From local model file", modifier = Modifier.clearAndSetSemantics {})
        }
      }
    }
  }

  // Import dialog
  if (showImportDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      ModelImportDialog(
        uri = uri,
        onDismiss = { showImportDialog = false },
        onDone = { info ->
          selectedImportedModelInfo.value = info
          showImportDialog = false
          showImportingDialog = true
        },
      )
    }
  }

  // Importing in progress dialog.
  if (showImportingDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      selectedImportedModelInfo.value?.let { info ->
        ModelImportingDialog(
          uri = uri,
          info = info,
          onDismiss = { showImportingDialog = false },
          onDone = {
            viewModel.addImportedLlmModel(info = it)
            showImportingDialog = false
          },
        )
      }
    }
  }

  // Unsupported file type dialog.
  if (showUnsupportedFileTypeDialog) {
    AlertDialog(
      onDismissRequest = { showUnsupportedFileTypeDialog = false },
      confirmButton = {
        Button(onClick = { showUnsupportedFileTypeDialog = false }) { Text("OK") }
      },
      title = { Text("Unsupported file type") },
      text = { Text("Only .task and .litertlm files are supported.") },
    )
  }

  // Unsupported web model dialog.
  if (showUnsupportedWebModelDialog) {
    AlertDialog(
      onDismissRequest = { showUnsupportedWebModelDialog = false },
      confirmButton = {
        Button(onClick = { showUnsupportedWebModelDialog = false }) { Text("OK") }
      },
      title = { Text("Unsupported model file") },
      text = { Text("Web model files are not supported.") },
    )
  }
}
