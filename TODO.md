- Size hats according to font size
- why aren't there hats in this file

Sometimes hats get lost:

```
package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.markEditorChange
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener

class TalonDocumentListener : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
        super.documentChanged(event)
        markEditorChange("document listener -> document area changed")

    }
}
```
