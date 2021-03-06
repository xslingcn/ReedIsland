/*
 *  Copyright 2020 Fishballzzz
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.lxj.xpopup.core;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.animator.BlurAnimator;
import com.lxj.xpopup.animator.EmptyAnimator;
import com.lxj.xpopup.animator.PopupAnimator;
import com.lxj.xpopup.animator.ScaleAlphaAnimator;
import com.lxj.xpopup.animator.ScrollScaleAnimator;
import com.lxj.xpopup.animator.ShadowBgAnimator;
import com.lxj.xpopup.animator.TranslateAlphaAnimator;
import com.lxj.xpopup.animator.TranslateAnimator;
import com.lxj.xpopup.enums.PopupStatus;
import com.lxj.xpopup.impl.FullScreenPopupView;
import com.lxj.xpopup.impl.PartShadowPopupView;
import com.lxj.xpopup.util.KeyboardUtils;
import com.lxj.xpopup.util.XPopupUtils;
import com.lxj.xpopup.util.navbar.NavigationBarObserver;
import com.lxj.xpopup.util.navbar.OnNavigationBarListener;

import java.util.ArrayList;
import java.util.Stack;

import static com.lxj.xpopup.enums.PopupAnimation.NoAnimation;

/**
 * Description: ????????????
 * Create by lxj, at 2018/12/7
 */
public abstract class BasePopupView extends FrameLayout implements OnNavigationBarListener, LifecycleObserver {
    private static final Stack<BasePopupView> stack = new Stack<>(); //??????????????????????????????
    public PopupInfo popupInfo;
    protected PopupAnimator popupContentAnimator;
    protected ShadowBgAnimator shadowBgAnimator;
    protected BlurAnimator blurAnimator;
    private final int touchSlop;
    public PopupStatus popupStatus = PopupStatus.Dismiss;
    protected boolean isCreated = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public BasePopupView(@NonNull Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        shadowBgAnimator = new ShadowBgAnimator(this);
        //  ??????Popup????????????View
        View contentView = LayoutInflater.from(context).inflate(getPopupLayoutId(), this, false);
        // ?????????????????????????????????????????????View?????????????????????
        contentView.setAlpha(0);
        addView(contentView);
    }

    /**
     * ???????????????
     */
    protected void init() {
        NavigationBarObserver.getInstance().register(getContext());
        NavigationBarObserver.getInstance().addOnNavigationBarListener(this);

        //1. ?????????Popup
        if (this instanceof AttachPopupView) {
            initPopupContent();
        } else if (!isCreated) {
            initPopupContent();
        }
        //apply size dynamic
        if (!(this instanceof FullScreenPopupView) && !(this instanceof ImageViewerPopupView)) {
            XPopupUtils.setWidthHeight(getTargetSizeView(),
                    (getMaxWidth() != 0 && getPopupWidth() > getMaxWidth()) ? getMaxWidth() : getPopupWidth(),
                    (getMaxHeight() != 0 && getPopupHeight() > getMaxHeight()) ? getMaxHeight() : getPopupHeight()
            );
        }
        if (!isCreated) {
            isCreated = true;
            onCreate();
            if (popupInfo.xPopupCallback != null) popupInfo.xPopupCallback.onCreated(this);
        }
        handler.postDelayed(initTask, 50);
    }

    private final Runnable initTask = new Runnable() {
        @Override
        public void run() {
            // ????????????????????????????????????????????????????????????????????????
            applySize(false);
            getPopupContentView().setAlpha(1f);

            //2. ?????????????????????
            collectAnimator();

            if (popupInfo.xPopupCallback != null) popupInfo.xPopupCallback.beforeShow(BasePopupView.this);

            //3. ????????????
            doShowAnimation();

            doAfterShow();
        }
    };

    private boolean hasMoveUp = false;

    private void collectAnimator() {
        if (this instanceof AttachPopupView && !(this instanceof PartShadowPopupView)) {
            popupContentAnimator = getPopupAnimator();

            //3. ????????????????????????
            if (popupInfo.hasShadowBg) {
                shadowBgAnimator.initAnimator();
            }
            if (popupInfo.hasBlurBg) {
                blurAnimator = new BlurAnimator(this);
                blurAnimator.hasShadowBg = popupInfo.hasShadowBg;
                blurAnimator.decorBitmap = XPopupUtils.view2Bitmap((XPopupUtils.context2Activity(this)).getWindow().getDecorView());
                blurAnimator.initAnimator();
            }
            if (popupContentAnimator != null) {
                popupContentAnimator.initAnimator();
            }
        } else if (popupContentAnimator == null) {
            // ?????????????????????????????????
            if (popupInfo.customAnimator != null) {
                popupContentAnimator = popupInfo.customAnimator;
                popupContentAnimator.targetView = getPopupContentView();
            } else {
                // ??????PopupInfo???popupAnimation????????????????????????????????????????????????popupAnimation?????????null????????????null
                popupContentAnimator = genAnimatorByPopupType();
                if (popupContentAnimator == null) {
                    popupContentAnimator = getPopupAnimator();
                }
            }

            //3. ????????????????????????
            if (popupInfo.hasShadowBg) {
                shadowBgAnimator.initAnimator();
            }
            if (popupInfo.hasBlurBg) {
                blurAnimator = new BlurAnimator(this);
                blurAnimator.hasShadowBg = popupInfo.hasShadowBg;
                blurAnimator.decorBitmap = XPopupUtils.view2Bitmap((XPopupUtils.context2Activity(this)).getWindow().getDecorView());
                blurAnimator.initAnimator();
            }
            if (popupContentAnimator != null) {
                popupContentAnimator.initAnimator();
            }
        }
    }

    @Override
    public void onNavigationBarChange(boolean show) {
        if (!show) {
            applyFull();
        } else {
            applySize(true);
        }
    }

    protected void applyFull() {
        FrameLayout.LayoutParams params = (LayoutParams) getLayoutParams();
        params.topMargin = 0;
        params.leftMargin = 0;
        params.bottomMargin = 0;
        params.rightMargin = 0;
        setLayoutParams(params);
    }

    protected void applySize(boolean isShowNavBar) {
    }

    public BasePopupView show() {
        Activity activity = XPopupUtils.context2Activity(this);
        if (activity == null || activity.isFinishing()) {
            return this;
        }
        if (popupStatus == PopupStatus.Showing) return this;
        popupStatus = PopupStatus.Showing;
        if (dialog != null && dialog.isShowing()) return BasePopupView.this;
        handler.post(attachTask);
        return this;
    }

    private final Runnable attachTask = new Runnable() {
        @Override
        public void run() {
            // 1. add PopupView to its dialog.
            attachDialog();
            if (getContext() instanceof FragmentActivity) {
                ((FragmentActivity) getContext()).getLifecycle().addObserver(BasePopupView.this);
            }
            //2. ????????????????????????
            popupInfo.decorView = (ViewGroup) dialog.getWindow().getDecorView();
            KeyboardUtils.registerSoftInputChangedListener(dialog.getWindow(), BasePopupView.this, new KeyboardUtils.OnSoftInputChangedListener() {
                @Override
                public void onSoftInputChanged(int height) {
                    if (height == 0) { // ?????????????????????
                        XPopupUtils.moveDown(BasePopupView.this);
                        hasMoveUp = false;
                    } else {
                        //when show keyboard, move up
                        //??????????????????????????????show???????????????
                        if (BasePopupView.this instanceof FullScreenPopupView && popupStatus == PopupStatus.Showing) {
                            return;
                        }
                        if (BasePopupView.this instanceof PartShadowPopupView && popupStatus == PopupStatus.Showing) {
                            return;
                        }
                        XPopupUtils.moveUpToKeyboard(height, BasePopupView.this);
                        hasMoveUp = true;
                    }
                }
            });

            // 3. do init???game start.
            init();
        }
    };

    protected FullScreenDialog dialog;

    private void attachDialog() {
        if (dialog == null) {
            dialog = new FullScreenDialog(getContext())
                    .setContent(this);
        }
        dialog.show();
    }

    protected void doAfterShow() {
        handler.removeCallbacks(doAfterShowTask);
        handler.postDelayed(doAfterShowTask, getAnimationDuration());
    }

    private final Runnable doAfterShowTask = new Runnable() {
        @Override
        public void run() {
            popupStatus = PopupStatus.Show;
            onShow();
            focusAndProcessBackPress();
            if (popupInfo != null && popupInfo.xPopupCallback != null)
                popupInfo.xPopupCallback.onShow(BasePopupView.this);
            //????????????????????????
            if (dialog == null) return;
            if (XPopupUtils.getDecorViewInvisibleHeight(dialog.getWindow()) > 0 && !hasMoveUp) {
                XPopupUtils.moveUpToKeyboard(XPopupUtils.getDecorViewInvisibleHeight(dialog.getWindow()), BasePopupView.this);
            }
        }
    };

    private ShowSoftInputTask showSoftInputTask;

    public void focusAndProcessBackPress() {
        if (popupInfo == null) {
            return;
        }
        if (popupInfo.isRequestFocus) {
            setFocusableInTouchMode(true);
            requestFocus();
            if (!stack.contains(this)) {
                stack.push(this);
            }
        }
        // ??????????????????????????????EditText?????????????????????EditText???????????????????????????
        setOnKeyListener(new BackPressListener());
        if (!popupInfo.autoFocusEditText) showSoftInput(this);

        //let all EditText can process back pressed.
        ArrayList<EditText> list = new ArrayList<>();
        XPopupUtils.findAllEditText(list, (ViewGroup) getPopupContentView());
        for (int i = 0; i < list.size(); i++) {
            final EditText et = list.get(i);
            et.setOnKeyListener(new BackPressListener());
            if (i == 0 && popupInfo.autoFocusEditText) {
                et.setFocusable(true);
                et.setFocusableInTouchMode(true);
                et.requestFocus();
                showSoftInput(et);
            }
        }
    }

    protected void showSoftInput(View focusView) {
        if (popupInfo.autoOpenSoftInput) {
            if (showSoftInputTask == null) {
                showSoftInputTask = new ShowSoftInputTask(focusView);
            } else {
                handler.removeCallbacks(showSoftInputTask);
            }
            handler.postDelayed(showSoftInputTask, 10);
        }
    }

    protected void dismissOrHideSoftInput() {
        if (KeyboardUtils.sDecorViewInvisibleHeightPre == 0)
            dismiss();
        else
            KeyboardUtils.hideSoftInput(BasePopupView.this);
    }

    static class ShowSoftInputTask implements Runnable {
        View focusView;
        boolean isDone = false;

        public ShowSoftInputTask(View focusView) {
            this.focusView = focusView;
        }

        @Override
        public void run() {
            if (focusView != null && !isDone) {
                isDone = true;
                KeyboardUtils.showSoftInput(focusView);
            }
        }
    }

    class BackPressListener implements OnKeyListener {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (popupInfo.isDismissOnBackPressed &&
                        (popupInfo.xPopupCallback == null || !popupInfo.xPopupCallback.onBackPressed(BasePopupView.this)))
                    dismissOrHideSoftInput();
                return true;
            }
            return false;
        }
    }

    /**
     * ??????PopupInfo???popupAnimation????????????????????????????????????????????????
     */
    protected PopupAnimator genAnimatorByPopupType() {
        if (popupInfo == null || popupInfo.popupAnimation == null) return null;
        switch (popupInfo.popupAnimation) {
            case ScaleAlphaFromCenter:
            case ScaleAlphaFromLeftTop:
            case ScaleAlphaFromRightTop:
            case ScaleAlphaFromLeftBottom:
            case ScaleAlphaFromRightBottom:
                return new ScaleAlphaAnimator(getPopupContentView(), popupInfo.popupAnimation);

            case TranslateAlphaFromLeft:
            case TranslateAlphaFromTop:
            case TranslateAlphaFromRight:
            case TranslateAlphaFromBottom:
                return new TranslateAlphaAnimator(getPopupContentView(), popupInfo.popupAnimation);

            case TranslateFromLeft:
            case TranslateFromTop:
            case TranslateFromRight:
            case TranslateFromBottom:
                return new TranslateAnimator(getPopupContentView(), popupInfo.popupAnimation);

            case ScrollAlphaFromLeft:
            case ScrollAlphaFromLeftTop:
            case ScrollAlphaFromTop:
            case ScrollAlphaFromRightTop:
            case ScrollAlphaFromRight:
            case ScrollAlphaFromRightBottom:
            case ScrollAlphaFromBottom:
            case ScrollAlphaFromLeftBottom:
                return new ScrollScaleAnimator(getPopupContentView(), popupInfo.popupAnimation);

            case NoAnimation:
                return new EmptyAnimator(getPopupContentView());
        }
        return null;
    }

    protected abstract int getPopupLayoutId();

    /**
     * ?????????????????????BasePopupView???????????????????????????
     *
     * @return
     */
    protected int getImplLayoutId() {
        return -1;
    }

    /**
     * ??????PopupAnimator????????????????????????PopupView???????????????????????????
     *
     * @return
     */
    protected PopupAnimator getPopupAnimator() {
        return null;
    }

    /**
     * ?????????onCreate????????????????????????????????????????????????
     */
    protected void initPopupContent() {
    }

    /**
     * do init.
     */
    protected void onCreate() {
    }

    protected void applyDarkTheme() {
    }

    /**
     * ??????????????????????????????2??????????????????????????????????????????????????????Content????????????
     * ??????????????????????????????Content???????????????
     */
    protected void doShowAnimation() {
        if (popupInfo.hasShadowBg && !popupInfo.hasBlurBg) {
            shadowBgAnimator.animateShow();
        } else if (popupInfo.hasBlurBg && blurAnimator != null) {
            blurAnimator.animateShow();
        }
        if (popupContentAnimator != null)
            popupContentAnimator.animateShow();
    }

    /**
     * ??????????????????????????????2??????????????????????????????????????????????????????Content????????????
     * ??????????????????????????????Content???????????????
     */
    protected void doDismissAnimation() {
        if (popupInfo.hasShadowBg && !popupInfo.hasBlurBg) {
            shadowBgAnimator.animateDismiss();
        } else if (popupInfo.hasBlurBg && blurAnimator != null) {
            blurAnimator.animateDismiss();
        }

        if (popupContentAnimator != null)
            popupContentAnimator.animateDismiss();
    }

    /**
     * ????????????View????????????PopupView???????????????????????????View?????????
     * ???????????????PopupView?????????????????????????????????????????????
     *
     * @return
     */
    public View getPopupContentView() {
        return getChildAt(0);
    }

    public View getPopupImplView() {
        return ((ViewGroup) getPopupContentView()).getChildAt(0);
    }

    public int getAnimationDuration() {
        return popupInfo.popupAnimation == NoAnimation ? 10 : XPopup.getAnimationDuration() + 10;
    }

    /**
     * ?????????????????????????????????????????????????????????wrap??????match??????????????????
     *
     * @return
     */
    protected int getMaxWidth() {
        return 0;
    }

    /**
     * ?????????????????????????????????????????????????????????wrap??????match??????????????????
     *
     * @return
     */
    protected int getMaxHeight() {
        return popupInfo.maxHeight;
    }

    /**
     * ???????????????????????????????????????????????????????????????getMaxWidth()??????
     *
     * @return
     */
    protected int getPopupWidth() {
        return 0;
    }

    /**
     * ???????????????????????????????????????????????????????????????getMaxHeight()??????
     *
     * @return
     */
    protected int getPopupHeight() {
        return 0;
    }

    protected View getTargetSizeView() {
        return getPopupContentView();
    }

    /**
     * ??????
     */
    public void dismiss() {
        handler.removeCallbacks(attachTask);
        handler.removeCallbacks(initTask);
        if (popupStatus == PopupStatus.Dismissing || popupStatus == PopupStatus.Dismiss) return;
        popupStatus = PopupStatus.Dismissing;
        clearFocus();
        if (popupInfo.xPopupCallback != null) popupInfo.xPopupCallback.beforeDismiss(this);
        beforeDismiss();
        doDismissAnimation();
        doAfterDismiss();
    }

    /**
     * ???????????????show???????????????????????????
     */
    public void smartDismiss() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                delayDismiss(XPopup.getAnimationDuration() + 50);
            }
        });
    }

    public void delayDismiss(long delay) {
        if (delay < 0) delay = 0;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismiss();
            }
        }, delay);
    }

    public void delayDismissWith(long delay, Runnable runnable) {
        this.dismissWithRunnable = runnable;
        delayDismiss(delay);
    }

    protected void doAfterDismiss() {
        if (popupInfo == null || popupInfo.decorView == null) return;
        // PartShadowPopupView?????????????????????????????????????????????????????????
        if (popupInfo.autoOpenSoftInput && !(this instanceof PartShadowPopupView)) KeyboardUtils.hideSoftInput(this);
        handler.removeCallbacks(doAfterDismissTask);
        handler.postDelayed(doAfterDismissTask, getAnimationDuration());
    }

    private final Runnable doAfterDismissTask = new Runnable() {
        @Override
        public void run() {
            if (popupInfo.autoOpenSoftInput && BasePopupView.this instanceof PartShadowPopupView)
                KeyboardUtils.hideSoftInput(BasePopupView.this);
            onDismiss();
            if (popupInfo != null && popupInfo.xPopupCallback != null) {
                popupInfo.xPopupCallback.onDismiss(BasePopupView.this);
            }
            if (dismissWithRunnable != null) {
                dismissWithRunnable.run();
                dismissWithRunnable = null;//no cache, avoid some bad edge effect.
            }
            popupStatus = PopupStatus.Dismiss;
            NavigationBarObserver.getInstance().removeOnNavigationBarListener(BasePopupView.this);

            if (!stack.isEmpty()) stack.pop();
            if (popupInfo != null && popupInfo.isRequestFocus) {
                if (!stack.isEmpty()) {
                    stack.get(stack.size() - 1).focusAndProcessBackPress();
                } else {
                    // ???????????????????????????????????????RecyclerView??????????????????????????????????????????
                    View needFocusView = popupInfo.decorView.findViewById(android.R.id.content);
                    if (needFocusView != null) {
                        needFocusView.setFocusable(true);
                        needFocusView.setFocusableInTouchMode(true);
                    }
                }
            }

            // ???????????????GameOver
            if (popupInfo != null && popupInfo.decorView != null) {
                dialog.dismiss();
            }
        }
    };

    Runnable dismissWithRunnable;

    public void dismissWith(Runnable runnable) {
        this.dismissWithRunnable = runnable;
        dismiss();
    }

    public boolean isShow() {
        return popupStatus != PopupStatus.Dismiss;
    }

    public boolean isDismiss() {
        return popupStatus == PopupStatus.Dismiss;
    }

    public void toggle() {
        if (isShow()) {
            dismiss();
        } else {
            show();
        }
    }

    /**
     * ?????????????????????????????????
     */
    protected void onDismiss() {
    }

    /**
     * ?????????????????????????????????
     */
    protected void beforeDismiss() {
    }

    /**
     * ?????????????????????????????????
     */
    protected void onShow() {
    }

    @OnLifecycleEvent(value = Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        destroy();
    }

    public void destroy() {
        if (dialog != null) dialog.dismiss();
        onDetachedFromWindow();
        if (popupInfo != null) {
            popupInfo.atView = null;
            popupInfo.watchView = null;
            popupInfo.xPopupCallback = null;
        }
        popupInfo = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stack.clear();
        handler.removeCallbacksAndMessages(null);
        NavigationBarObserver.getInstance().removeOnNavigationBarListener(BasePopupView.this);
        if (popupInfo != null) {
            if (popupInfo.decorView != null)
                KeyboardUtils.removeLayoutChangeListener(popupInfo.decorView, BasePopupView.this);
            if (popupInfo.isDestroyOnDismiss) { //????????????isDestroyOnDismiss?????????????????????
                popupInfo.atView = null;
                popupInfo.watchView = null;
                popupInfo.xPopupCallback = null;
                popupInfo = null;
            }
        }
        popupStatus = PopupStatus.Dismiss;
        showSoftInputTask = null;
        hasMoveUp = false;
        if (blurAnimator != null && blurAnimator.decorBitmap != null && !blurAnimator.decorBitmap.isRecycled()) {
            blurAnimator.decorBitmap.recycle();
            blurAnimator.decorBitmap = null;
        }
    }

    private float x, y;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // ?????????????????????????????????????????????PopupContentView??????????????????????????????????????????????????????,???????????????dismiss
        Rect rect = new Rect();
        getPopupContentView().getGlobalVisibleRect(rect);
        if (!XPopupUtils.isInRect(event.getX(), event.getY(), rect)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = event.getX();
                    y = event.getY();
                    break;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - x;
                    float dy = event.getY() - y;
                    float distance = (float) Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
                    if (distance < touchSlop && popupInfo.isDismissOnTouchOutside) {
                        dismiss();
                    }
                    x = 0;
                    y = 0;
                    break;
            }
        }
        if (dialog != null && popupInfo.isClickThrough) dialog.passClick(event);
        return true;
    }

}
