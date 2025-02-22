package org.jetbrains.completion.full.line.language

import com.intellij.ui.IconManager
import org.jetbrains.kotlin.idea.KotlinIcons
import javax.swing.Icon

class KotlinIconSet : IconSet {
  override val regular: Icon = KotlinIcons.SMALL_LOGO
  override val redCode: Icon = IconManager.getInstance().getIcon("/icons/kotlin/redCode.svg", this::class.java)
}
