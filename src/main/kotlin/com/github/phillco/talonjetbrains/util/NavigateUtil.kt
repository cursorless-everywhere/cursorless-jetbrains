package com.github.phillco.talonjetbrains.talon

import com.github.phillco.talonjetbrains.control.CommandResponse
import com.github.phillco.talonjetbrains.sync.getEditor
import com.github.phillco.talonjetbrains.sync.getProject
import com.github.phillco.talonjetbrains.util.caretLanguage
import com.github.phillco.talonjetbrains.util.containingFunctionAtCaret
import com.intellij.lang.Language
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.psi.PsiElement

enum class NavigationType {
    FILE {
        override fun current(): Document {
            return getEditor()!!.document
        }
    },
    FUNCTION {
        override fun current(): PsiElement? {
            return containingFunctionAtCaret(getEditor()!!)
        }
    },
    LANGUAGE {
        override fun current(): Language? {
            return caretLanguage(getEditor()!!)
        }
    };

    abstract fun current(): Any?
}

/**
 * Navigates through the file hierarchy until a different file is found.
 *
 * Mimics the behavior of the Visual Studio Code actions workbench.action.openPreviousRecentlyUsedEditor
 * and workbench.action.openNextRecentlyUsedEditor.
 */
fun navigate(forward: Boolean, type: NavigationType): CommandResponse {
    val project = getProject()
    val historyManager = IdeDocumentHistory.getInstance(project)

    val verb = if (forward) "forward" else "back"

    var current: Any? = null
    var original: Any? = null

    var steps = 0
    var success = false

    ApplicationManager.getApplication().invokeAndWait {
        println("Navigating $verb by $type")
        ApplicationManager.getApplication().runReadAction {
            original = type.current()
            current = type.current()

            println("Original: $original")
            while ((if (forward) historyManager.isForwardAvailable else historyManager.isBackAvailable) && steps < 100) {
                println("Current: $current, steps: $steps")

                if (forward) {
                    historyManager.forward()
                } else {
                    historyManager.back()
                }
                current = type.current()
                steps++

                // NOTE(pcohen): we want to check if the current isn't null in the case of navigating
                // across files they don't support functions (such as Talon files).
                // We want to skip over those in case there are history entries later that do support them.
                success = current != original && current != null
                if (success) {
                    println("Found different; breaking")
                    break
                }
            }

            println("Navigated $verb $steps steps to $current, changed: ${current != original}")

            if (!success) {
                // revert the navigation
                repeat(steps) {
                    if (forward) {
                        historyManager.back()
                    } else {
                        historyManager.forward()
                    }
                }
            }
        }

    }

    val changed = current != original

    if (!success) {
        var explanation =
            "Navigation stack (checked $steps entries) didn't include a different ${
                type.toString().lowercase()
            }"
        if (current == null) {
            // NOTE(pcohen): this is just to explain why you might end up on a Talon file
            // when navigating by function
            explanation += " (last entry was also null)"
        }
        Notifications.Bus.notify(
            Notification(
                "talon",
                "Unable to navigate ${
                    type.toString().lowercase()
                }/$verb",
                explanation,
                NotificationType.WARNING
            )
        )
    }

    return if (!changed) {
        CommandResponse("Failed; no different file found to go $verb to")
    } else {
        CommandResponse("OK, navigated $verb $steps steps to $current")
    }
}
