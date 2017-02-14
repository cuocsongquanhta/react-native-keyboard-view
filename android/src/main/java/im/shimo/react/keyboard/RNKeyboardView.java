package im.shimo.react.keyboard;


import java.util.ArrayList;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupWindow;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.uimanager.DisplayMetricsHolder;
import com.facebook.react.uimanager.JSTouchDispatcher;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.RootView;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.facebook.react.views.view.ReactViewGroup;


public class RNKeyboardView extends ViewGroup implements LifecycleEventListener {

    private KeyboardRootViewGroup mHostView;
    private PopupWindow mWindow;
    private InputMethodManager mInputMethodManager;
    private Rect mVisibleViewArea;
    private int mMinKeyboardHeightDetected;
    private float mScale;
    private boolean mVisible;

    public RNKeyboardView(Context context) {
        super(context);
        ((ReactContext) context).addLifecycleEventListener(this);
        mHostView = new KeyboardRootViewGroup(context);
        mInputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        mVisibleViewArea = new Rect();
        mMinKeyboardHeightDetected = (int) PixelUtil.toPixelFromDIP(60);
        mScale = DisplayMetricsHolder.getScreenDisplayMetrics().density;

        mWindow = new PopupWindow(mHostView);
        mWindow.setWidth(WindowManager.LayoutParams.MATCH_PARENT);
        mWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Do nothing as we are laid out by UIManager
    }

    @Override
    public void addView(View child, int index) {
        mHostView.addView(child, index);
        showPopupWindow();
    }

    @Override
    public int getChildCount() {
        return mHostView.getChildCount();
    }

    @Override
    public View getChildAt(int index) {
        return mHostView.getChildAt(index);
    }

    @Override
    public void removeView(View child) {
        mHostView.removeView(child);
    }

    @Override
    public void removeViewAt(int index) {
        removeView(getChildAt(index));
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> outChildren) {
        // Explicitly override this to prevent accessibility events being passed down to children
        // Those will be handled by the mHostView which lives in the PopupWindow
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Explicitly override this to prevent accessibility events being passed down to children
        // Those will be handled by the mHostView which lives in the PopupWindow
        return false;
    }

    public void onDropInstance() {
        ((ReactContext) getContext()).removeLifecycleEventListener(this);
        mWindow.dismiss();
    }

    public void setHeight(float height) {
        mWindow.setHeight((int) (height * mScale));
    }

    public void setVisible(boolean visible) {
        mVisible = visible;
        if (visible) {
            showPopupWindow();
        } else {
            mWindow.dismiss();
        }
    }

    @Override
    public void onHostResume() {
        // We show the PopupWindow again when the host resumes
        showPopupWindow();
    }

    @Override
    public void onHostPause() {
        // We dismiss the PopupWindow and reconstitute it onHostResume
        mWindow.dismiss();
    }

    @Override
    public void onHostDestroy() {
        // Drop the instance if the host is destroyed which will dismiss the PopupWindow
        onDropInstance();
    }

    protected void showPopupWindow() {
        if (!mWindow.isShowing() && mHostView.hasContent()) {

            if (checkKeyboardStatus()) {
                hideContent();
            } else {
                showContent();
            }

            if (mVisible) {
                mWindow.showAtLocation(getRootView(), Gravity.BOTTOM, 0, 0);
            }

        }
    }

    protected boolean checkKeyboardStatus() {
        getRootView().getWindowVisibleDisplayFrame(mVisibleViewArea);
        return DisplayMetricsHolder.getWindowDisplayMetrics().heightPixels - mVisibleViewArea.bottom > mMinKeyboardHeightDetected;
    }

    public void openKeyboard() {
        if (!checkKeyboardStatus()) {
            hideContent();
            mHostView.post(new Runnable() {
                @Override
                public void run() {
                    mInputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                }
            });
        }
    }

    public void closeKeyboard() {
        if (checkKeyboardStatus()) {
            mInputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            getRootView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    showContent();
                    getRootView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }
    }

    public void toggleKeyboard() {
        if (checkKeyboardStatus()) {
            closeKeyboard();
        } else {
            openKeyboard();
        }
    }

    public boolean close() {
        if (checkKeyboardStatus()) {
            mInputMethodManager.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY);
            return true;
        }

        return false;
    }

    private void hideContent() {
        mHostView.setContentVisible(false);
    }

    private void showContent() {
        mHostView.setContentVisible(true);
    }


    static class KeyboardRootViewGroup extends ReactViewGroup implements RootView {

        private final JSTouchDispatcher mJSTouchDispatcher = new JSTouchDispatcher(this);

        public KeyboardRootViewGroup(Context context) {
            super(context);
        }

        public void setContentVisible(boolean visible) {
            ViewGroup container = (ViewGroup) getChildAt(0);
            if (visible) {
                container.getChildAt(0).setVisibility(View.VISIBLE);
            } else {
                container.getChildAt(0).setVisibility(View.GONE);
            }
        }

        @Override
        protected void onSizeChanged(final int w, final int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (getChildCount() > 0) {
                ((ReactContext) getContext()).runOnNativeModulesQueueThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                ((ReactContext) getContext()).getNativeModule(UIManagerModule.class)
                                        .updateNodeSize(getChildAt(0).getId(), w, h);
                            }
                        });
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            try {
                mJSTouchDispatcher.handleTouchEvent(event, getEventDispatcher());
                return super.onInterceptTouchEvent(event);
            } catch (Exception e) {
                return true;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            try {
                mJSTouchDispatcher.handleTouchEvent(event, getEventDispatcher());
                super.onTouchEvent(event);
                // In case when there is no children interested in handling touch event, we return true from
                // the root view in order to receive subsequent events related to that gesture
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void onChildStartedNativeGesture(MotionEvent androidEvent) {
            mJSTouchDispatcher.onChildStartedNativeGesture(androidEvent, getEventDispatcher());
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            // No-op - override in order to still receive events to onInterceptTouchEvent
            // even when some other view disallow that
        }

        private EventDispatcher getEventDispatcher() {
            ReactContext reactContext = (ReactContext) getContext();
            return reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
        }

        public boolean hasContent() {
            ViewGroup container = (ViewGroup) getChildAt(0);
            return container != null && container.getChildCount() > 0;
        }
    }
}