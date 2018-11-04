package com.ebaryice.refreshlayout;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;


public class RefreshRecycleLayout extends LinearLayout implements View.OnTouchListener {

    private static final String TAG = "RefreshRecycleLayout";
    /**
     * 下拉状态
     */
    public static final int STATUS_PULL_TO_REFRESH = 0;
    /**
     * 释放立即刷新状态
     */
    public static final int STATUS_RELEASE_TO_REFRESH = 1;
    /**
     * 正在刷新状态
     */
    public static final int STATUS_REFRESHING = 2;
    /**
     * 刷新完成状态
     */
    public static final int STATUS_REFRESHED = 3;
    /**
     * 下拉header回滚的速度
     */
    public static final int SCROLL_SPEED = -20;
    /**
     * 下拉刷新的回调接口
     */
    private PullToRefreshListener mListener;
    /**
     * 下拉的header
     */
    private View header;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private ImageView arrow;
    private TextView description;
    /**
     * 下拉header的布局参数
     */
    private MarginLayoutParams headerLayoutParams;
    /**
     * 下拉header的高度
     */
    private int hideHeaderHeight;
    /**
     * 记录当前的状态
     */
    private int currentStatus = STATUS_REFRESHED;
    /**
     * 记录上一次的状态，避免重复操作
     */
    private int lastStatus = currentStatus;
    /**
     * 手指的屏幕Y坐标
     */
    private float yDown;
    /**
     * 在被判定为滚动之前用户手指可以滑动的最大值
     */
    private int touchSlop;
    /**
     * 是否已经加载过一次layout,onLayout中的初始化只需要加载一次
     */
    private boolean loadOnce;

    private int mId;
    /**
     * 判断是否可以下拉,只有RecyclerView滚动到头的时候才允许下拉
     */
    private boolean ableToPull;


    public RefreshRecycleLayout(Context context) {
        this(context,null);
    }

    public RefreshRecycleLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        header = LayoutInflater.from(context).inflate(R.layout.refresh_recycle_layout,null,true);
        arrow = header.findViewById(R.id.iv_refresh_arrow);
        description = header.findViewById(R.id.tv_refresh);
        progressBar = header.findViewById(R.id.pb_refresh);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(VERTICAL);
        addView(header,0);
    }

    /**
     * 进行一些关键性的初始化操作,比如：将header向上偏移进行隐藏,
     * 给RecyclerView注册touch事件
     * @param changed
     * @param l
     * @param t
     * @param r
     * @param b
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && !loadOnce){
            hideHeaderHeight = -header.getMeasuredHeight();
            headerLayoutParams = (MarginLayoutParams) header.getLayoutParams();
            Log.d("fuck","hideLayoutHeight "+hideHeaderHeight);
            headerLayoutParams.topMargin = hideHeaderHeight;
            recyclerView = (RecyclerView) getChildAt(1);
            header.setLayoutParams(headerLayoutParams);
            recyclerView.setOnTouchListener(this);
            loadOnce = true;
        }
    }

    //用于旋转目标的动画计算(暂时不考虑)
    //int preDistance = 0;

    /**
     * 当RecyclerView被触摸时调用,处理各种逻辑
     * @param v
     * @param event
     * @return
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        setIsAbleToPull(event);
        if (ableToPull){
            switch (event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    yDown = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float yMove = event.getRawY();
                    int distance = (int) (yMove - yDown);
                    //如果手指是下滑状态,并且header是完全隐藏,就屏蔽下拉事件
                    if (distance <= 0 && headerLayoutParams.topMargin <= hideHeaderHeight){
                        return false;
                    }
                    if (distance < touchSlop){
                        return false;
                    }
                    if (currentStatus != STATUS_REFRESHING){
                        if (headerLayoutParams.topMargin >= 0){
                            currentStatus = STATUS_RELEASE_TO_REFRESH;
                        }else {
                            currentStatus = STATUS_PULL_TO_REFRESH;
                        }
                        //通过偏移header的topMargin值来实现下拉效果
                        headerLayoutParams.topMargin = (int) ((distance / 2.0) + hideHeaderHeight);
                        header.setLayoutParams(headerLayoutParams);
                        //preDistance = distance;
                        //!!! 添加旋转图形动画//
                    }
                    break;
                case MotionEvent.ACTION_UP:
                default:
                    if (currentStatus == STATUS_RELEASE_TO_REFRESH){
                        // 如果是释放立即刷新状态,就去调用正在刷新的任务
                        new RefreshingTask().execute();
                    }else if (currentStatus == STATUS_PULL_TO_REFRESH){
                        // 如果是下拉状态,就去调用隐藏header的任务
                        new HideHeaderTask().execute();
                    }
                    break;
            }
            // 一直更新下拉header的信息
            if (currentStatus == STATUS_PULL_TO_REFRESH
                    || currentStatus == STATUS_RELEASE_TO_REFRESH){
                updateHeaderView();
                // 当前正处于下拉或者释放状态,要让RecyclerView失去焦点,
                // 否则被点击的那一项会一直处于check状态
                recyclerView.setPressed(false);
                recyclerView.setFocusable(false);
                recyclerView.setFocusableInTouchMode(false);
                lastStatus = currentStatus;
                // 用过return屏蔽掉RecyclerView的滚动事件
                return true;
            }
        }
        return false;
    }

    /**
     * 设置下拉监听器
     * @param listener
     * @param id 为了防止不同界面的下拉刷新在上次更新时间上有冲突,所以传入了id作为tag
     */
    public void setOnRefreshListener(PullToRefreshListener listener,int id){
        mListener = listener;
        mId = id;
    }

    /**
     * 更新header的信息
     */
    private void updateHeaderView() {
        if (lastStatus != currentStatus){
            if (currentStatus == STATUS_PULL_TO_REFRESH){ //下拉状态
                description.setText("下拉刷新");
                arrow.setVisibility(VISIBLE);
                progressBar.setVisibility(GONE);
                rotateArrow();
            }else if (currentStatus == STATUS_RELEASE_TO_REFRESH){ //释放状态
                description.setText("释放立即刷新");
                arrow.setVisibility(VISIBLE);
                progressBar.setVisibility(GONE);
                rotateArrow();
            }else if (currentStatus == STATUS_REFRESHING){ //刷新状态
                description.setText("正在刷新...");
                arrow.clearAnimation();
                arrow.setVisibility(GONE);
                progressBar.setVisibility(VISIBLE);
            }
        }
    }

    /**
     * 根据当前的状态来旋转箭头
     */
    private void rotateArrow(){
        arrow.clearAnimation();
        float pivotX = arrow.getWidth() / 2f;
        float pivotY = arrow.getHeight() / 2f;
        float fromDegree = 0f;
        float toDegree = 0f;
        if (currentStatus == STATUS_PULL_TO_REFRESH){
//            fromDegree = 180f;
//            toDegree = 360f;
        }else if (currentStatus == STATUS_RELEASE_TO_REFRESH){
            fromDegree = 0f;
            toDegree = 180f;
        }
        RotateAnimation animation = new RotateAnimation(fromDegree,toDegree,pivotX,pivotY);
        animation.setDuration(100);
        animation.setFillAfter(true);
        arrow.startAnimation(animation);
    }

    /**
     * 判断是滚动RecyclerView还是进行下拉
     * @param event
     */
    private void setIsAbleToPull(MotionEvent event) {
        View firstChild = recyclerView.getChildAt(0);
        if (firstChild != null){
            LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
            int firstVisiblePos = lm.findFirstVisibleItemPosition();
            if (firstVisiblePos == 0 && firstChild.getTop() == 0){
                if (!ableToPull){
                    yDown = event.getRawY();
                }
                // 如果首个元素的上边缘距离父布局的值为0,就说明滚动到了最顶部,此时允许下拉
                ableToPull = true;
            }else {
                if (headerLayoutParams.topMargin != hideHeaderHeight){
                    headerLayoutParams.topMargin = hideHeaderHeight;
                    header.setLayoutParams(headerLayoutParams);
                }
                ableToPull = false;
            }
        }else {
            // RecyclerView中没有元素,允许下拉
            ableToPull = true;
        }
    }

    /**
     * 刷新完成
     */
    public void finishRefreshing(){
        currentStatus = STATUS_REFRESHED;
        new HideHeaderTask().execute();
    }

    class RefreshingTask extends AsyncTask<Void,Integer,Void>{
        @Override
        protected Void doInBackground(Void... voids) {
            int topMargin = headerLayoutParams.topMargin;
            while (true){
                Log.d(TAG,"topMargin "+topMargin);
                topMargin = topMargin + SCROLL_SPEED;
                if (topMargin <= 0){
                    topMargin = 0;
                    break;
                }
                publishProgress(topMargin);
                sleep(10);
            }
            currentStatus = STATUS_REFRESHING;
            publishProgress(0);
            if (mListener != null){
                mListener.onRefresh();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... topMargins) {
            updateHeaderView();
            headerLayoutParams.topMargin = topMargins[0];
            header.setLayoutParams(headerLayoutParams);
        }
    }

    class HideHeaderTask extends AsyncTask<Void,Integer,Integer>{

        @Override
        protected Integer doInBackground(Void... voids) {
            int topMargin = headerLayoutParams.topMargin;
            while (true){
                topMargin = topMargin + SCROLL_SPEED;
                if (topMargin <= hideHeaderHeight){
                    topMargin = hideHeaderHeight;
                    break;
                }
                publishProgress(topMargin);
                sleep(10);
            }
            return topMargin;
        }

        @Override
        protected void onProgressUpdate(Integer... topMargin) {
            Log.d("fuck","update "+topMargin[0]);
            headerLayoutParams.topMargin = topMargin[0];
            header.setLayoutParams(headerLayoutParams);
        }

        @Override
        protected void onPostExecute(Integer topMargin) {
            Log.d("fuck","execute "+topMargin);
            headerLayoutParams.topMargin = topMargin;
            header.setLayoutParams(headerLayoutParams);
            currentStatus = STATUS_REFRESHED;
        }
    }

    /**
     * 线程睡眠
     * @param time
     */
    private void sleep(int time){
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public interface PullToRefreshListener {
        /**
         * 刷新时回调此方法(此方法是在子线程调用的,可以不用另开线程进行耗时操作)
         */
        void onRefresh();
    }
}

