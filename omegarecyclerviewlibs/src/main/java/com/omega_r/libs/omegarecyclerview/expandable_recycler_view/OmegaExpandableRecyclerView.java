package com.omega_r.libs.omegarecyclerview.expandable_recycler_view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.omega_r.libs.omegarecyclerview.OmegaRecyclerView;
import com.omega_r.libs.omegarecyclerview.R;
import com.omega_r.libs.omegarecyclerview.expandable_recycler_view.animation.AnimationHelper;
import com.omega_r.libs.omegarecyclerview.expandable_recycler_view.animation.standard_animations.DropDownItemAnimator;
import com.omega_r.libs.omegarecyclerview.expandable_recycler_view.animation.standard_animations.FadeItemAnimator;
import com.omega_r.libs.omegarecyclerview.expandable_recycler_view.data.ExpandableViewData;
import com.omega_r.libs.omegarecyclerview.expandable_recycler_view.data.FlatGroupingList;
import com.omega_r.libs.omegarecyclerview.expandable_recycler_view.data.GroupProvider;
import com.omega_r.libs.omegarecyclerview.expandable_recycler_view.data.Range;
import com.omega_r.libs.omegarecyclerview.expandable_recycler_view.layout_manager.ExpandableLayoutManager;
import com.omega_r.libs.omegarecyclerview.expandable_recycler_view.sticky.StickyGroupsAdapter;
import com.omega_r.libs.omegarecyclerview.sticky_header.StickyHeaderAdapter;
import com.omega_r.libs.omegarecyclerview.sticky_header.StickyHeaderDecoration;
import com.omega_r.libs.omegarecyclerview.sticky_header.StickyHeaderOnlyTopDecoration;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OmegaExpandableRecyclerView extends OmegaRecyclerView {
    private static final String TAG = OmegaExpandableRecyclerView.class.getName();

    public static final int CHILD_ANIM_DEFAULT = 0;
    public static final int CHILD_ANIM_FADE = 1;
    public static final int CHILD_ANIM_DROPDOWN = 2;

    public static final int EXPAND_MODE_SINGLE = 0;
    public static final int EXPAND_MODE_MULTIPLE = 1;

    private static final String KEY_ADAPTER_DATA = "OmegaExpandableRecyclerView.KEY_ADAPTER_DATA";
    private static final String KEY_RECYCLER_DATA = "OmegaExpandableRecyclerView.KEY_RECYCLER_DATA";

    @ExpandMode
    private int mExpandMode = EXPAND_MODE_SINGLE;

    @ExpandAnimation
    private int mChildExpandAnimation = CHILD_ANIM_DEFAULT;

    @Nullable
    private Rect mHeaderRect;

    @Nullable
    private Adapter.GroupViewHolder mHeaderViewHolder;

    private boolean mIsTouchEventStartsInStickyHeader;

    //region Recycler

    public OmegaExpandableRecyclerView(Context context) {
        super(context);
        init();
    }

    public OmegaExpandableRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        parseAttributes(attrs, 0);
        init();
    }

    public OmegaExpandableRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        parseAttributes(attrs, defStyle);
        init();
    }


    private void init() {
        setItemAnimator(requestItemAnimator());
        setChildDrawingOrderCallback(new RecyclerView.ChildDrawingOrderCallback() {
            @Override
            public int onGetChildDrawingOrder(int childCount, int i) {
                return childCount - i - 1;
            }
        });
    }

    @Nullable
    private ItemAnimator requestItemAnimator() {
        switch (mChildExpandAnimation) {
            case CHILD_ANIM_DEFAULT:
                return new DefaultItemAnimator();
            case CHILD_ANIM_DROPDOWN:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return new DropDownItemAnimator();
                } else {
                    Log.e(TAG, "DropDownItemAnimator supported only since Lollipop");
                    return new DefaultItemAnimator();
                }
            case CHILD_ANIM_FADE:
                return new FadeItemAnimator();
        }
        return null;
    }

    private void parseAttributes(AttributeSet attributeSet, int defStyleAttr) {
        TypedArray attrs = getContext().getTheme()
                .obtainStyledAttributes(attributeSet, R.styleable.OmegaExpandableRecyclerView, defStyleAttr, 0);
        try {
            mChildExpandAnimation = attrs.getInteger(R.styleable.OmegaExpandableRecyclerView_childAnimation, CHILD_ANIM_DEFAULT);
            mExpandMode = attrs.getInteger(R.styleable.OmegaExpandableRecyclerView_expandMode, EXPAND_MODE_SINGLE);
        } finally {
            attrs.recycle();
        }

    }

    @Override
    protected void initDefaultLayoutManager(@Nullable AttributeSet attrs, int defStyleAttr) {
        if (getLayoutManager() == null) {
            setLayoutManager(new ExpandableLayoutManager(getContext(), attrs, defStyleAttr, 0));
        }
    }

    @Override
    public void setLayoutManager(@Nullable LayoutManager layoutManager) {
        if (layoutManager != null && !(layoutManager instanceof ExpandableLayoutManager)) {
            throw new IllegalStateException("LayoutManager " + layoutManager.toString() + " should be ExpandableLayoutManager");
        }
        super.setLayoutManager(layoutManager);
    }

    @Override
    public void setAdapter(RecyclerView.Adapter adapter) {
        if (!(adapter instanceof Adapter))
            throw new IllegalStateException("Adapter should extend OmegaExpandableRecyclerView.Adapter");
        super.setAdapter(adapter);
    }

    @ExpandAnimation
    public int getChildExpandAnimation() {
        return mChildExpandAnimation;
    }

    public void setChildExpandAnimation(@ExpandAnimation int childExpandAnimation) {
        mChildExpandAnimation = childExpandAnimation;
        setItemAnimator(requestItemAnimator());
    }

    @ExpandMode
    public int getExpandMode() {
        return mExpandMode;
    }

    public void setExpandMode(@ExpandMode int expandMode) {
        mExpandMode = expandMode;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();

        RecyclerView.Adapter adapter = getAdapter();
        if (adapter != null) {
            bundle.putParcelable(KEY_ADAPTER_DATA, ((Adapter) adapter).onSaveInstanceState());
        }

        bundle.putParcelable(KEY_RECYCLER_DATA, super.onSaveInstanceState());

        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            if (bundle.containsKey(KEY_RECYCLER_DATA)) {
                super.onRestoreInstanceState(bundle.getParcelable(KEY_RECYCLER_DATA));
            }
            if (bundle.containsKey(KEY_ADAPTER_DATA) && getAdapter() != null) {
                ((Adapter) getAdapter()).onRestoreInstanceState(bundle.getBundle(KEY_ADAPTER_DATA));
            }
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    protected StickyHeaderDecoration provideStickyHeaderDecoration(StickyHeaderAdapter adapter) {
        if (getAdapter() instanceof StickyGroupsAdapter) {
            return new StickyHeaderOnlyTopDecoration(adapter);
        } else {
            return super.provideStickyHeaderDecoration(adapter);
        }
    }

    public void notifyHeaderPosition(Adapter.GroupViewHolder headerHolder, Rect viewRect) {
        mHeaderViewHolder = headerHolder;
        mHeaderRect = viewRect;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mHeaderRect != null && mHeaderViewHolder != null && getAdapter() != null) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_UP:
                    boolean shouldReturn = false;
                    if (isEventInRect(ev, mHeaderRect) && mIsTouchEventStartsInStickyHeader) {
                        ((Adapter) getAdapter()).notifyExpandFired(mHeaderViewHolder);
                        shouldReturn = true;
                    }
                    mIsTouchEventStartsInStickyHeader = false;
                    if (shouldReturn) return true;
                    break;
                case MotionEvent.ACTION_DOWN:
                    mIsTouchEventStartsInStickyHeader = isEventInRect(ev, mHeaderRect);
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isEventInRect(MotionEvent ev, Rect rect) {
        return ev.getX() >= rect.left && ev.getX() <= rect.right &&
                ev.getY() >= rect.top && ev.getY() <= rect.bottom;
    }
    // endregion

    //region Adapter
    public static abstract class Adapter<G, CH> extends OmegaRecyclerView.Adapter<BaseViewHolder> {

        private static final int VH_TYPE_GROUP = 238956;
        private static final int VH_TYPE_CHILD = 238957;

        private static final long ANTI_SPAM_DELAY = 400;

        private long mAntiSpamTimestamp = SystemClock.elapsedRealtime();

        private FlatGroupingList<G, CH> items;
        private OmegaExpandableRecyclerView recyclerView;

        protected abstract GroupViewHolder provideGroupViewHolder(@NonNull ViewGroup viewGroup);

        protected abstract ChildViewHolder provideChildViewHolder(@NonNull ViewGroup viewGroup);

        @SafeVarargs
        public Adapter(ExpandableViewData<G, CH>... expandableViewData) {
            items = new FlatGroupingList<>(Arrays.asList(expandableViewData));
        }

        @SafeVarargs
        public Adapter(GroupProvider<G, CH>... groupProviders) {
            items = new FlatGroupingList<>(convertFrom(groupProviders));
        }

        public Adapter() {
            items = new FlatGroupingList<>(Collections.<ExpandableViewData<G, CH>>emptyList());
        }

        @NonNull
        private List<ExpandableViewData<G, CH>> convertFrom(GroupProvider<G, CH>[] groupProviders) {
            List<ExpandableViewData<G, CH>> expandableViewData = new ArrayList<>();
            for (GroupProvider<G, CH> groupProvider : groupProviders) {
                expandableViewData.add(ExpandableViewData.of(groupProvider.provideGroup(), groupProvider.provideStickyId(), groupProvider.provideChilds()));
            }
            return expandableViewData;
        }

        public final void setItems(@NonNull List<ExpandableViewData<G, CH>> expandableViewData) {
            items = new FlatGroupingList<>(expandableViewData);
            tryNotifyDataSetChanged();
        }

        @SafeVarargs
        public final void setItems(ExpandableViewData<G, CH>... expandableViewData) {
            setItems(Arrays.asList(expandableViewData));
        }

        @SafeVarargs
        public final void setItems(GroupProvider<G, CH>... groupProviders) {
            setItems(convertFrom(groupProviders));
        }

        @NonNull
        @Override
        public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int itemType) {
            switch (itemType) {
                case VH_TYPE_CHILD:
                    return provideChildViewHolder(viewGroup);
                case VH_TYPE_GROUP:
                    return provideGroupViewHolder(viewGroup);
            }
            throw new IllegalStateException("Incorrect view type");
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(@NonNull BaseViewHolder baseViewHolder, int position) {
            baseViewHolder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.getVisibleItemsCount();
        }

        @Override
        public int getItemViewType(int position) {
            switch (items.getType(position)) {
                case GROUP:
                    return VH_TYPE_GROUP;
                case CHILD:
                    return VH_TYPE_CHILD;
            }
            return super.getItemViewType(position);
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
            this.recyclerView = (OmegaExpandableRecyclerView) recyclerView;
        }

        @Override
        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onDetachedFromRecyclerView(recyclerView);
            this.recyclerView = null;
        }

        public void expand(G group) {
            if (recyclerView != null && recyclerView.getExpandMode() == EXPAND_MODE_SINGLE) {
                List<G> expandedGroups = items.getExpandedGroups();
                for (G expandedGroup : expandedGroups) {
                    collapse(expandedGroup);
                }
            }

            items.onExpandStateChanged(group, true);

            int childsCount = items.getChildsCount(group);
            int positionStart = items.getVisiblePosition(group) + 1;

            if (childsCount > 0) {

                if (recyclerView != null) {
                    ExpandableLayoutManager lm = (ExpandableLayoutManager) recyclerView.getLayoutManager();
                    if (lm != null) lm.setAddedRange(Range.ofLength(positionStart, childsCount));
                }

                tryNotifyItemRangeInserted(positionStart, childsCount);
            }
        }

        public void collapse(G group) {
            items.onExpandStateChanged(group, false);

            int childsCount = items.getChildsCount(group);
            if (childsCount > 0) {
                tryNotifyItemRangeRemoved(items.getVisiblePosition(group) + 1, childsCount);
            }
        }

        private void notifyExpandFired(GroupViewHolder viewHolder) {
            long lastTimestamp = mAntiSpamTimestamp;
            mAntiSpamTimestamp = SystemClock.elapsedRealtime();
            if (mAntiSpamTimestamp - lastTimestamp < ANTI_SPAM_DELAY) return;

            G group = viewHolder.getItem();
            if (items.isExpanded(group)) {
                collapse(group);
                viewHolder.onCollapse(viewHolder, items.getGroupIndex(group));
            } else {
                expand(group);
                viewHolder.onExpand(viewHolder, items.getGroupIndex(group));
            }
        }

        protected Parcelable onSaveInstanceState() {
            return items.onSaveInstanceState();
        }

        protected void onRestoreInstanceState(Bundle savedInstanceState) {
            items.onRestoreInstanceState(savedInstanceState);
            tryNotifyDataSetChanged();
        }

        public List<ExpandableViewData<G, CH>> getItems() {
            return items.getItems();
        }

        public ExpandableViewData<G, CH> getItem(int position) {
            return items.getDataAtVisiblePosition(position);
        }

        public abstract class GroupViewHolder extends BaseViewHolder<G> {

            private View currentExpandFiringView = itemView;
            private final OnClickListener clickListener = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    notifyExpandFired(GroupViewHolder.this);
                }
            };

            protected abstract void onExpand(GroupViewHolder viewHolder, int groupIndex);

            protected abstract void onCollapse(GroupViewHolder viewHolder, int groupIndex);

            public GroupViewHolder(ViewGroup parent, @LayoutRes int res) {
                super(parent, res);
                setExpandFiringView(itemView);
            }

            protected void setExpandFiringView(View firingView) {
                currentExpandFiringView.setOnClickListener(null);
                currentExpandFiringView = firingView;
                currentExpandFiringView.setOnClickListener(clickListener);
            }
        }

        public abstract class ChildViewHolder extends BaseViewHolder<CH> {

            public View contentView;

            public final AnimationHelper animationHelper = new AnimationHelper();

            public ChildViewHolder(ViewGroup parent, @LayoutRes int res) {
                this(LayoutInflater.from(parent.getContext()).inflate(res, parent, false));
            }

            private ChildViewHolder(View view) {
                super(new ChildClippingFrameLayout(view.getContext()));
                itemView.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
                ((ViewGroup) itemView).addView(view);
                contentView = view;
            }

            @Override
            public void bind(CH item) {
                super.bind(item);
                animationHelper.visibleAdapterPosition = getAdapterPosition();
            }
        }
    }
    //endregion

    //region ViewHolders
    private static abstract class BaseViewHolder<T> extends OmegaRecyclerView.ViewHolder {
        private T item;

        BaseViewHolder(ViewGroup parent, @LayoutRes int res) {
            super(parent, res);
        }

        BaseViewHolder(View view) {
            super(view);
        }

        public void bind(T item) {
            this.item = item;
            onBind(item);
        }

        @NonNull
        T getItem() {
            return item;
        }

        protected abstract void onBind(T item);
    }

    //endregion

    @IntDef({CHILD_ANIM_DEFAULT, CHILD_ANIM_FADE, CHILD_ANIM_DROPDOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExpandAnimation {
        // nothing
    }

    @IntDef({EXPAND_MODE_SINGLE, EXPAND_MODE_MULTIPLE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExpandMode {
        // nothing
    }
}
