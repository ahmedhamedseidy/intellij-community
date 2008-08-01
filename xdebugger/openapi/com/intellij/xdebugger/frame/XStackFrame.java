/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.xdebugger.frame;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XStackFrame extends XValueContainer {

  /**
   * @return an object which will be used to determine if stack frame changed after step
   */
  @Nullable
  public Object getEqualityObject() {
    return null;
  }

  /**
   * Implement to support evaluation in debugger (conditional breakpoints, logging message on breakpoint, "Evaluate" action, watches)
   * @return evaluator instance
   */
  @Nullable
  public XDebuggerEvaluator getEvaluator() {
    return null;
  }

  /**
   * @return source position corresponding to stack frame
   */
  @Nullable 
  public XSourcePosition getSourcePosition() {
    return null;
  }

  /**
   * Customize presentation of the stack frame in frames list
   * @param component component
   */
  public void customizePresentation(final SimpleColoredComponent component) {
    XSourcePosition position = getSourcePosition();
    if (position != null) {
      component.append(position.getFile().getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.append(":" + position.getLine(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.setIcon(DebuggerIcons.STACK_FRAME_ICON);
    }
    else {
      component.append(XDebuggerBundle.message("invalid.frame"), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }
}
