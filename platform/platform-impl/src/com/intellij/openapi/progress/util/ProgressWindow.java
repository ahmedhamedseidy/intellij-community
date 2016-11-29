/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@SuppressWarnings({"NonStaticInitializer"})
public class ProgressWindow extends ProgressIndicatorBase implements BlockingProgressIndicator, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressWindow");

  /**
   * This constant defines default delay for showing progress dialog (in millis).
   *
   * @see #setDelayInMillis(int)
   */
  public static final int DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS = 300;

  private ProgressDialog myDialog;

  final Project myProject;
  final boolean myShouldShowCancel;
  String myCancelText;

  private String myTitle = null;

  private boolean myStoppedAlready = false;
  private boolean myStarted = false;
  protected boolean myBackgrounded = false;
  private String myProcessId = "<unknown>";
  @Nullable private volatile Runnable myBackgroundHandler;
  private int myDelayInMillis = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;

  public interface Listener {
    void progressWindowCreated(ProgressWindow pw);
  }

  public static final Topic<Listener> TOPIC = Topic.create("progress window", Listener.class);

  public ProgressWindow(boolean shouldShowCancel, Project project) {
    this(shouldShowCancel, false, project);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project) {
    this(shouldShowCancel, shouldShowBackground, project, null);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project, String cancelText) {
    this(shouldShowCancel, shouldShowBackground, project, null, cancelText);
  }

  public ProgressWindow(boolean shouldShowCancel,
                        boolean shouldShowBackground,
                        @Nullable Project project,
                        JComponent parentComponent,
                        String cancelText) {
    myProject = project;
    myShouldShowCancel = shouldShowCancel;
    myCancelText = cancelText;
    setModalityProgress(shouldShowBackground ? null : this);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).progressWindowCreated(this);
    myDialog = ProgressDialogFactory.SERVICE.getInstance().createProgressDialog(this, project, cancelText, shouldShowBackground, parentComponent);

    if (myProject != null) {
      Disposer.register(myProject, this);
    }
  }

  @Override
  public synchronized void start() {
    LOG.assertTrue(!isRunning());
    LOG.assertTrue(!myStoppedAlready);

    super.start();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      prepareShowDialog();
    }

    myStarted = true;
  }

  /**
   * There is a possible case that many short (in terms of time) progress tasks are executed in a small amount of time.
   * Problem: UI blinks and looks ugly if we show progress dialog for every such task (every dialog disappears shortly).
   * Solution is to postpone showing progress dialog in assumption that the task may be already finished when it's
   * time to show the dialog.
   * <p/>
   * Default value is {@link #DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS}
   *
   * @param delayInMillis   new delay time in milliseconds
   */
  public void setDelayInMillis(int delayInMillis) {
    myDelayInMillis = delayInMillis;
  }

  public synchronized boolean isStarted() {
    return myStarted;
  }

  protected void prepareShowDialog() {
    // We know at least about one use-case that requires special treatment here: many short (in terms of time) progress tasks are
    // executed in a small amount of time. Problem: UI blinks and looks ugly if we show progress dialog that disappears shortly
    // for each of them. Solution is to postpone the tasks of showing progress dialog. Hence, it will not be shown at all
    // if the task is already finished when the time comes.
    myDialog.prepareShowDialog(myDelayInMillis);
  }

  @Override
  public void startBlocking() {
    startBlocking(EmptyRunnable.getInstance());
  }

  public void startBlocking(@NotNull Runnable init) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (this) {
      LOG.assertTrue(!isRunning());
      LOG.assertTrue(!myStoppedAlready);
    }

    init.run();

    myDialog.startBlocking(myShouldShowCancel);
  }

  @NotNull
  public String getProcessId() {
    return myProcessId;
  }

  public void setProcessId(@NotNull String processId) {
    myProcessId = processId;
  }

  public void showDialog() {
    if (!isRunning() || isCanceled()) {
      return;
    }

    myDialog.show();
  }

  @Override
  public void startNonCancelableSection() {
    if (isCancelable()) {
      enableCancel(false);
    }
    super.startNonCancelableSection();
  }

  @Override
  public void finishNonCancelableSection() {
    super.finishNonCancelableSection();
    if (isCancelable()) {
      enableCancel(true);
    }
  }

  @Override
  public void setIndeterminate(boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    update();
  }

  @Override
  public synchronized void stop() {
    LOG.assertTrue(!myStoppedAlready);

    super.stop();

    if (isDialogShowing()) {
      myDialog.setWillBeSheduledForRestore();
    }

    UIUtil.invokeLaterIfNeeded(() ->{

        if (myDialog != null) {
          myDialog.hide();
        }



        synchronized (this) {
          myStoppedAlready = true;
        }

      Disposer.dispose(this);
    });

    SwingUtilities.invokeLater(EmptyRunnable.INSTANCE); // Just to give blocking dispatching a chance to go out.
  }

  private boolean isDialogShowing() {
    return myDialog != null && myDialog.isShowing();
  }

  public void background() {
    final Runnable backgroundHandler = myBackgroundHandler;
    if (backgroundHandler != null) {
      backgroundHandler.run();
      return;
    }

    if (myDialog != null) {
      myBackgrounded = true;
      myDialog.background();
      myDialog = null;
    }
  }

  public boolean isBackgrounded() {
    return myBackgrounded;
  }

  @Override
  public void setText(String text) {
    if (!Comparing.equal(text, getText())) {
      super.setText(text);
      update();
    }
  }

  @Override
  public void setFraction(double fraction) {
    if (fraction != getFraction()) {
      super.setFraction(fraction);
      update();
    }
  }

  @Override
  public void setText2(String text) {
    if (!Comparing.equal(text, getText2())) {
      super.setText2(text);
      update();
    }
  }

  private void update() {
    if (myDialog != null) {
      myDialog.update();
    }
  }

  public void setTitle(String title) {
    if (!Comparing.equal(title, myTitle)) {
      myTitle = title;
      update();
    }
  }

  public String getTitle() {
    return myTitle;
  }

  public void setBackgroundHandler(@Nullable Runnable backgroundHandler) {
    myBackgroundHandler = backgroundHandler;
    myDialog.setShouldShowBackground(backgroundHandler != null);
  }

  public void setCancelButtonText(String text) {
    if (myDialog != null) {
      myDialog.changeCancelButtonText(text);
    }
    else {
      myCancelText = text;
    }
  }

  IdeFocusManager getFocusManager() {
    return IdeFocusManager.getInstance(myProject);
  }

  @Override
  public void dispose() {
    stopSystemActivity();
    if (isRunning()) {
      cancel();
    }
  }

  @Override
  public boolean isPopupWasShown() {
    return myDialog != null && myDialog.isPopupWasShown();
  }

  protected void enableCancel(boolean enable) {
    if (myDialog != null) {
      myDialog.enableCancelButtonIfNeeded(enable);
    }
  }

  @Override
  public String toString() {
    return getTitle() + " " + System.identityHashCode(this) + ": running="+isRunning()+"; canceled="+isCanceled();
  }
}
