# formio-android

An Android library that renders [Form.io](https://form.io) JSON schemas as native mobile forms — no WebView, no JavaScript runtime. Built with Kotlin, Material Design 3, and a dark-first UI.

> **Open source and community-driven.** Contributions, new components, and improvements are welcome!

---

## Screenshots

<table>
  <tr>
    <td align="center"><b>Basic fields</b></td>
    <td align="center"><b>Select &amp; checkboxes</b></td>
    <td align="center"><b>File upload &amp; panels</b></td>
    <td align="center"><b>Datagrid &amp; signature</b></td>
  </tr>
  <tr>
    <td><img src="im/1.png" width="180"/></td>
    <td><img src="im/2.png" width="180"/></td>
    <td><img src="im/3.png" width="180"/></td>
    <td><img src="im/4.png" width="180"/></td>
  </tr>
</table>

---

## Features

- ✅ Renders Form.io JSON schemas natively (no WebView)
- ✅ Multi-page wizard with tab navigation and progress bar
- ✅ Conditional logic (`conditional`, `customConditional`)
- ✅ `calculateValue` expressions with live re-evaluation
- ✅ Field validation (required, minLength, maxLength, pattern, min/max, custom JS)
- ✅ Camera and gallery photo capture
- ✅ Signature pad with zoom preview
- ✅ Map/location picker
- ✅ Datagrid (repeating rows)
- ✅ Panel, Columns, Tabs layout components
- ✅ `refreshOn` / `clearOnRefresh` cascading selects
- ✅ Button with custom JS execution (`app.ws()`, `form.getComponent()`, `moment()`)
- ✅ Dark theme with fully overridable Material 3 color tokens
- ✅ Returns form data as `Map<String, Any?>` — you control persistence

---

## Supported components

| Form.io type | Status |
|---|---|
| `textfield`, `email`, `phoneNumber` | ✅ |
| `textarea` | ✅ |
| `number`, `currency` | ✅ |
| `select` (static, URL, custom) | ✅ |
| `radio` | ✅ |
| `selectboxes` | ✅ |
| `tags` | ✅ |
| `datetime` | ✅ |
| `file` (camera + gallery) | ✅ |
| `signature` | ✅ |
| `map` / `location` | ✅ |
| `htmlelement` | ✅ |
| `button` | ✅ |
| `panel`, `well` | ✅ |
| `columns` | ✅ |
| `tabs` | ✅ |
| `datagrid` | ✅ |
| `hidden` | ✅ |

---

## Installation

### Step 1 — Add JitPack to your project

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```groovy
// settings.gradle (Groovy)
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2 — Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.gjagomez:formio-android:1.0.0")
}
```

```groovy
// build.gradle (Groovy)
dependencies {
    implementation 'com.github.gjagomez:formio-android:1.0.0'
}
```

---

## Usage

### Launch a form

```kotlin
// 1. Register the launcher in your Activity or Fragment
val formLauncher = registerForActivityResult(FormioRenderer.Contract()) { result ->
    result ?: return@registerForActivityResult

    val formData: Map<String, Any?> = result.formData
    val isFinal: Boolean = result.isFinal  // false = draft save, true = completed

    // Persist the data however your app needs
}

// 2. Launch with a Form.io schema JSON string
formLauncher.launch(
    FormioRenderer.Input(
        schemaJson  = mySchema,          // String — the Form.io JSON schema
        prefillData = emptyMap(),        // Optional: pre-populate fields
        title       = "My Form"          // Displayed in the toolbar
    )
)
```

### Edit an existing submission

```kotlin
val prefill: Map<String, Any?> = mapOf(
    "firstName" to "Juan",
    "dpi"       to "1234567890101"
)

formLauncher.launch(
    FormioRenderer.Input(
        schemaJson  = mySchema,
        prefillData = prefill,
        title       = "Edit submission"
    )
)
```

### Remote selects (`dataSrc: "url"`)

Selects can load their options from any JSON endpoint configured in the Form.io designer — no app code needed:

```json
{
    "type": "select",
    "label": "Model",
    "key": "model",
    "dataSrc": "url",
    "data": {
        "url": "https://vpic.nhtsa.dot.gov/api/vehicles/getmodelsformake/honda?format=json"
    },
    "selectValues": "Results",
    "valueProperty": "Model_Name",
    "template": "<span>{{ item.Model_Name }}</span>",
    "lazyLoad": true
}
```

| Property | Description |
|---|---|
| `data.url` | GET endpoint. Supports `{{ data.otherField }}` placeholders, re-resolved with current form values |
| `data.headers` | Optional request headers `[{ "key": "...", "value": "..." }]` |
| `selectValues` | Dot-path to the array inside the response (e.g. `"Results"`, `"data.items"`). Omit if the response is already an array |
| `valueProperty` | Dot-path to the stored value inside each item |
| `template` | Item label, e.g. `"<span>{{ item.name }}</span>"` (HTML is stripped) |
| `lazyLoad` | `true` = fetch on first tap, `false` = fetch when the field renders |

Cascading selects work out of the box: combine `refreshOn: "make"` + `clearOnRefresh: true` with a URL like `https://api.example.com/models?make={{ data.make }}` — when `make` changes, the dependent select clears and reloads. Responses are cached per resolved URL, and long remote lists automatically get the searchable bottom-sheet picker.

### Result explained

| Field | Type | Description |
|---|---|---|
| `formData` | `Map<String, Any?>` | All field values keyed by the Form.io component `key` |
| `isFinal` | `Boolean` | `true` when the user completed the last page, `false` when they used the draft save button |

---

## Theming

The library ships with a dark Material 3 theme (`Theme.FormioRenderer`). Every color is overridable in your app's `res/values/colors.xml`:

```xml
<!-- res/values/colors.xml in your app -->
<resources>
    <!-- Override any of these to match your brand -->
    <color name="accent_green">#00C896</color>
    <color name="bg_primary">#0F0F0F</color>
    <color name="bg_card">#1A1A1A</color>
    <color name="text_primary">#F0F0F0</color>
    <color name="text_secondary">#A0A0A0</color>
    <color name="error_color">#CF6679</color>
</resources>
```

---

## Try the sample app

A complete working example is included in the [`sample/`](sample/) folder.

It loads the Form.io JSON schema from [`sample/src/main/res/raw/example_schema.json`](sample/src/main/res/raw/example_schema.json) and demonstrates the full launch → result flow in under 30 lines of code.

**To run it:**

```bash
git clone https://github.com/gjagomez/formio-android.git
cd formio-android/sample
./gradlew installDebug
```

> The sample is a standalone Gradle project that consumes the library from `../library`, so open the `sample/` folder (not the repo root) in Android Studio.

**What the sample does:**

```kotlin
// 1. Register the launcher
val formLauncher = registerForActivityResult(FormioRenderer.Contract()) { result ->
    result ?: return@registerForActivityResult

    val status = if (result.isFinal) "FINAL" else "DRAFT"
    // result.formData → Map<String, Any?> with all collected field values
    showData(result.formData, status)
}

// 2. Read schema from any source (raw resource, string, API response…)
val schemaJson = resources.openRawResource(R.raw.example_schema).bufferedReader().readText()

// 3. Launch
formLauncher.launch(
    FormioRenderer.Input(
        schemaJson  = schemaJson,
        prefillData = emptyMap(),
        title       = "Example Form"
    )
)
```

---

## Requirements

| | |
|---|---|
| Min SDK | 29 (Android 10) |
| Target SDK | 34 (Android 14) |
| Language | Kotlin |
| UI toolkit | Material Design 3 |

---

## Roadmap

- [ ] Light theme support
- [ ] `signature` — upload from gallery
- [ ] `file` — multiple file upload
- [ ] `survey` component
- [ ] `address` component
- [ ] Offline-first data queue (bring-your-own persistence)
- [ ] Accessibility (TalkBack support)
- [ ] English / i18n string overrides

---

## Contributing

Contributions are very welcome! This library is designed to grow with the community.

1. Fork the repository
2. Create a feature branch: `git checkout -b feat/my-new-component`
3. Commit your changes: `git commit -m "feat: add survey component"`
4. Push to the branch: `git push origin feat/my-new-component`
5. Open a Pull Request

**Areas where help is especially appreciated:**
- New Form.io component types
- Light theme
- Tests
- Documentation / examples in other languages

Please open a GitHub Issue before starting large changes so we can align on the approach.

---

## License

```
MIT License

Copyright (c) 2024 gjagomez

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
