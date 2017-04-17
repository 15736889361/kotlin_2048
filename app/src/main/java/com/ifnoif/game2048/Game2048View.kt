package com.ifnoif.game2048

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import java.util.*


/**
 * Created by shen on 17/4/12.
 */

class Game2048View : FrameLayout {
    companion object {

        var TAG = "Game2048View";
    }

    private var TYPE_GOOD: String = "GOOD";
    private var TYPE_MERGE: String = "MERGE";
    private var TYPE_MOVE: String = "MOVE";

    private var mTouchSlop: Int = 0;
    private var mStarted: Boolean = false;

    private val mDuration: Long = 300;
    private var mColumnSize: Int;
    private lateinit var mBlockArray: Array<Array<GameUtil.Block>>;
    /**
     * 滑动后剩余的空白位置
     */
    private var mEmptyPointList: ArrayList<Point> = ArrayList<Point>()
    /**
     * 滑动后需要移动 的Action
     */
    private var mActionList: ArrayList<GameUtil.Block> = ArrayList<GameUtil.Block>()
    private var mGameUtil = GameUtil();


    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0) {

        SoundPoolManager.init(context, TYPE_GOOD, R.raw.good);
        SoundPoolManager.init(context, TYPE_MERGE, R.raw.merge);
        SoundPoolManager.init(context, TYPE_MOVE, R.raw.move);

        val vc = ViewConfiguration.get(getContext())
        mTouchSlop = vc.scaledTouchSlop;
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        val typeArray: TypedArray = context!!.obtainStyledAttributes(attrs,
                R.styleable.Game2048View)
        mColumnSize = typeArray.getInteger(R.styleable.Game2048View_grid, 4);

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(widthSize, widthSize);
    }

    fun start() {
        Log.d(TAG, "开始游戏")
        reset();
        doNext();
        mStarted = true;
    }

    fun doNext() {
        if (checkGameOver()) {
            Log.d(TAG, "游戏结束")
            var dialog = AlertDialog.Builder(context).setTitle("Game Over").setMessage(String.format("您本次得分%d,是否再玩一遍", totalScore))
                    .setPositiveButton("确定", DialogInterface.OnClickListener { dialog, which -> start(); })
                    .setNegativeButton("取消", DialogInterface.OnClickListener { dialog, which -> reset();invalidate(); }).create()
            dialog.show()
            return;
        }
        var randomIndex = Random().nextInt(mEmptyPointList.size);
        var point = mEmptyPointList.removeAt(randomIndex);

        var randomValue = getRandomValue();
        (mBlockArray[point.y])[point.x].value = randomValue;

        var view = createView(point, randomValue);
        addView(view, LayoutParams(width / mColumnSize, width / mColumnSize));

        view.translationX = (point.x * width.toFloat()) / mColumnSize;
        view.translationY = (point.y * height.toFloat()) / mColumnSize;

        invalidate();

        Log.d(TAG, "生成一个方块(" + point.x + "," + point.y + "),大小:" + randomValue);
    }

    fun scroll(direction: GameUtil.Direction) {
        Log.d(TAG, "往" + direction + "滑动");


        mEmptyPointList.clear();
        mActionList.clear();

        mGameUtil.scroll(mBlockArray, direction, mEmptyPointList, mActionList);

        Log.d(TAG, "所有移动如下:")
        for (block in mActionList) {
            Log.d(TAG, block.getActionStr())
        }


        var callback: Runnable = Runnable { doNext() };
        doActionAnimation(callback);
    }

    fun doActionAnimation(callback: Runnable) {
        if (mActionList.size == 0) {
            callback.run();
            return;
        }
        Log.d(TAG, "开始滑动==")
        var animatorSet: AnimatorSet = AnimatorSet();
        animatorSet.duration = mDuration
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator?) {
                Log.d(TAG, "动画全部完成");
                callback.run();
            }

            override fun onAnimationCancel(animation: Animator?) {
            }

            override fun onAnimationRepeat(animation: Animator?) {
            }

            override fun onAnimationStart(animation: Animator?) {
            }
        })

        for (block in mActionList) {
            val needRemoveView = block.changeX >= 0;
            var removeTranslation: Float = 0F;
            var targetRemoveView: BlockView? = null;
            if (needRemoveView) {
                if (block.x == block.pX) {
                    removeTranslation = Math.abs((block.changeY * width).toFloat() / mColumnSize);
                } else {
                    removeTranslation = Math.abs((block.changeX * width).toFloat() / mColumnSize);
                }
                targetRemoveView = getView(block.changeX, block.changeY);

            } else {
                play(TYPE_MOVE);
            }
            var scrollHorizontal: Boolean = (block.pY == block.y);

            var startOffset = (width * (if (scrollHorizontal) block.pX else block.pY)).toFloat() / mColumnSize;
            var targetOffset = (width * (if (scrollHorizontal) block.x else block.y)).toFloat() / mColumnSize;

            Log.d(TAG, block.getActionStr() + " 像素从" + startOffset + "到" + targetOffset);

            var view = getView(block.pX, block.pY);
            if (view == null) {
                Log.e(TAG, "获取(" + block.pX + "," + block.pY + ")为空");
                continue;
            }
            view!!.needRemoveView = needRemoveView;
            view.removeTranslation = removeTranslation + ((if (targetOffset > startOffset) -0.02F else 0.02F) * width / mColumnSize);
            view.removeTranslation = Math.min(Math.max(0F, view.removeTranslation), width.toFloat());

            var animation: ValueAnimator = ObjectAnimator.ofFloat(view, (if (scrollHorizontal) "translationX" else "translationY"), startOffset, targetOffset);
            animation.duration = mDuration;
            animation.addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {
                }

                override fun onAnimationCancel(animation: Animator?) {
                }

                override fun onAnimationEnd(animation: Animator?) {
                    Log.d(TAG, block.getActionStr() + " ==完成,像素:" + (if (scrollHorizontal) view.translationX else view.translationY));
                }

                override fun onAnimationStart(animation: Animator?) {
                    //修改View中的坐标,不等动画结束
                    view.point.x = block.x;
                    view.point.y = block.y;
                }
            })


            animation.addUpdateListener { animator ->
                var currentTranslation = (animator!!.animatedValue as Float);

                if (targetOffset > startOffset) {

                }
                if (view.needRemoveView && (if (targetOffset > startOffset) currentTranslation >= view.removeTranslation else currentTranslation <= view.removeTranslation)) {
                    //删除合并的view
                    removeView(targetRemoveView)

                    addScore(view.value);

                    view.needRemoveView = false;
                    view.value = view.value * 2;
                    view.setText(view.value.toString());

                    play(TYPE_MERGE);
                    Log.d(TAG, "改变" + "(" + view.point.x + "," + view.point.y + ")的值为" + view.value);
                } else {
                    Log.d(TAG, "移动 currentTranslation：" + currentTranslation + " removeTranslation:" + view.removeTranslation);
                }
            }
            animatorSet.playSequentially(animation);
        }
        animatorSet.start()
    }

    fun getView(x: Int, y: Int): BlockView? {
        for (index in 0..childCount - 1) {
            var child = (getChildAt(index) as BlockView);
            if (child.point.x == x && child.point.y == y) {
                return child;
            }
        }
        return null;
    }

    /**
     * return 2 or 4
     */
    fun getRandomValue(): Int {
        return 2 + Random().nextInt(2) * 2;
    }

    fun createView(point: Point, value: Int): BlockView {
        return BlockView(context, point, value);
    }

    fun checkGameOver(): Boolean {
        //TODO 提前计算能不能减少位置
        return mEmptyPointList.size == 0;//没有空白位置，且滑动也不能减少位置
    }

    fun reset() {
        Log.d(TAG, "初始化")
        mStarted = false;
        totalScore = 0;
        onScoreChanged()
        removeAllViews();
        mEmptyPointList.clear();
        mBlockArray = Array(mColumnSize, { y -> Array(mColumnSize, { x -> GameUtil.Block(x, y, 0) }) });
        for (y in 0..mColumnSize - 1) {
            for (x in 0..mColumnSize - 1) {
                mEmptyPointList.add(Point(x, y))
            }
        }
    }

    fun play(type: String) {
        SoundPoolManager.play(type)
    }

    class BlockView : TextView {
        lateinit var point: Point;
        var value: Int;
        var needRemoveView: Boolean = false;
        var removeTranslation: Float = 0F;

        constructor(context: Context, point: Point, value: Int) : super(context) {
            this.point = point;
            this.value = value;
            gravity = Gravity.CENTER;
            setText("" + value);
            setTextColor(Color.RED);
            setTextSize(28F)
            setTypeface(Typeface.DEFAULT_BOLD);
            this.setBackgroundColor(Color.GRAY);
        }

        fun updatePoint(x: Int, y: Int) {
            point.x = x;
            point.y = y;
        }
    }

    var touchDownX: Float = 0f;
    var touchDownY: Float = 0f;
    var scrolled: Boolean = false;
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x;
                touchDownY = event.y;
                scrolled = false;
            }
            MotionEvent.ACTION_MOVE -> {
                if (mStarted && !scrolled) {

                    var tmpX = event.x;
                    var tmpY = event.y;
                    var deltaX = Math.abs(tmpX - touchDownX);
                    var deltaY = Math.abs(tmpY - touchDownY);
                    if (deltaX > deltaY && deltaX > mTouchSlop) {
                        scroll(if (tmpX > touchDownX) GameUtil.Direction.Right else GameUtil.Direction.Left)
                        scrolled = true;
                    } else if (deltaY > deltaX && deltaY > mTouchSlop) {
                        scroll(if (tmpY > touchDownY) GameUtil.Direction.Bottom else GameUtil.Direction.Top)
                        scrolled = true;
                    }
                }
            }
        }
        return true;
    }

    var totalScore: Int = 0;
    fun addScore(scoreValue: Int) {
        totalScore += scoreValue;

        onScoreChanged();
    }

    fun onScoreChanged() {
        ((context as GameActivity)).onScoreChanged(totalScore);
    }

}