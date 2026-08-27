package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentHomeHeadBinding
import com.lagradost.cloudstream3.databinding.FragmentHomeHeadTvBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountEditDialog
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountSelectLinear
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showOptionSelectStringRes
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarMargin

class HomeParentItemAdapterPreview(
    private val viewModel: HomeViewModel,
    private val accountViewModel: AccountViewModel
) : ParentItemAdapter(
    id = "HomeParentItemAdapterPreview".hashCode(),
    clickCallback = {
        viewModel.click(it)
    }, moreInfoClickCallback = {
        viewModel.popup(it)
    }, expandCallback = {
        viewModel.expand(it)
    }) {
    override val headers = 1
    override fun onCreateHeader(parent: ViewGroup): ViewHolderState<Bundle> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isLayout(TV or EMULATOR)) FragmentHomeHeadTvBinding.inflate(
            inflater,
            parent,
            false
        ) else FragmentHomeHeadBinding.inflate(inflater, parent, false)

        if (binding is FragmentHomeHeadTvBinding && isLayout(EMULATOR)) {
            binding.homeBookmarkParentItemMoreInfo.isVisible = true

            val marginInDp = 50
            val density = binding.horizontalScrollChips.context.resources.displayMetrics.density
            val marginInPixels = (marginInDp * density).toInt()

            val params = binding.horizontalScrollChips.layoutParams as ViewGroup.MarginLayoutParams
            params.marginEnd = marginInPixels
            binding.horizontalScrollChips.layoutParams = params
            binding.homeWatchParentItemTitle.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                ContextCompat.getDrawable(
                    parent.context,
                    R.drawable.ic_baseline_arrow_forward_24
                ),
                null
            )
        }

        return HeaderViewHolder(binding, viewModel, accountViewModel)
    }

    override fun onBindHeader(holder: ViewHolderState<Bundle>) {
        (holder as? HeaderViewHolder)?.bind()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolderState<Bundle>) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewDetachedFromWindow()
            }
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolderState<Bundle>) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewAttachedToWindow()
            }
        }
    }

    private class HeaderViewHolder(
        val binding: ViewBinding,
        val viewModel: HomeViewModel,
        accountViewModel: AccountViewModel,
    ) :
        ViewHolderState<Bundle>(binding) {

        override fun save(): Bundle =
            Bundle().apply {
                putParcelable(
                    "resumeRecyclerView",
                    resumeRecyclerView.layoutManager?.onSaveInstanceState()
                )
                putParcelable(
                    "bookmarkRecyclerView",
                    bookmarkRecyclerView.layoutManager?.onSaveInstanceState()
                )
            }

        override fun restore(state: Bundle) {
            state.getSafeParcelable<Parcelable>("resumeRecyclerView")?.let { recycle ->
                resumeRecyclerView.layoutManager?.onRestoreInstanceState(recycle)
            }
            state.getSafeParcelable<Parcelable>("bookmarkRecyclerView")?.let { recycle ->
                bookmarkRecyclerView.layoutManager?.onRestoreInstanceState(recycle)
            }
        }

        private val resumeAdapter = ResumeItemAdapter(
            nextFocusUp = itemView.nextFocusUpId,
            nextFocusDown = itemView.nextFocusDownId,
            removeCallback = { v ->
                try {
                    val context = v.context ?: return@ResumeItemAdapter
                    val builder: AlertDialog.Builder =
                        AlertDialog.Builder(context)
                    builder.apply {
                        setTitle(R.string.clear_history)
                        setMessage(
                            context.getString(R.string.delete_message).format(
                                context.getString(
                                    R.string.continue_watching
                                )
                            )
                        )
                        setNegativeButton(R.string.cancel) { _, _ -> /*NO-OP*/ }
                        setPositiveButton(R.string.delete) { _, _ ->
                            DataStoreHelper.deleteAllResumeStateIds()
                            viewModel.reloadStored()
                        }
                        show().setDefaultFocus()
                    }
                } catch (t: Throwable) {
                    logError(t)
                }
            },
            clickCallback = { callback ->
                if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                    viewModel.click(callback)
                    return@ResumeItemAdapter
                }
                callback.view.context?.getActivity()?.showOptionSelectStringRes(
                    callback.view,
                    callback.card.posterUrl,
                    listOf(
                        R.string.action_open_watching,
                        R.string.action_remove_watching
                    ),
                    listOf(
                        R.string.action_open_play,
                        R.string.action_open_watching,
                        R.string.action_remove_watching
                    )
                ) { (isTv, actionId) ->
                    when (actionId + if (isTv) 0 else 1) {
                        // play
                        0 -> {
                            viewModel.click(
                                SearchClickCallback(
                                    START_ACTION_RESUME_LATEST,
                                    callback.view,
                                    -1,
                                    callback.card
                                )
                            )
                        }
                        // info
                        1 -> {
                            viewModel.click(
                                SearchClickCallback(
                                    SEARCH_ACTION_LOAD,
                                    callback.view,
                                    -1,
                                    callback.card
                                )
                            )
                        }
                        // remove
                        2 -> {
                            val card = callback.card
                            if (card is DataStoreHelper.ResumeWatchingResult) {
                                DataStoreHelper.removeLastWatched(card.parentId)
                                viewModel.reloadStored()
                            }
                        }
                    }
                }
            })

        private val bookmarkAdapter = HomeChildItemAdapter(
            id = "bookmarkAdapter".hashCode(),
            nextFocusUp = itemView.nextFocusUpId,
            nextFocusDown = itemView.nextFocusDownId
        ) { callback ->
            if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                viewModel.click(callback)
                return@HomeChildItemAdapter
            }

            (callback.view.context?.getActivity() as? MainActivity)?.loadPopup(
                callback.card,
                load = false
            )
        }

        private val resumeHolder: View = itemView.findViewById(R.id.home_watch_holder)
        private val resumeRecyclerView: RecyclerView =
            itemView.findViewById(R.id.home_watch_child_recyclerview)
        private val bookmarkHolder: View = itemView.findViewById(R.id.home_bookmarked_holder)
        private val bookmarkRecyclerView: RecyclerView =
            itemView.findViewById(R.id.home_bookmarked_child_recyclerview)

        private val headProfilePic: ImageView? = itemView.findViewById(R.id.home_head_profile_pic)
        private val headProfilePicCard: View? =
            itemView.findViewById(R.id.home_head_profile_padding)

        private val topPadding: View? = itemView.findViewById(R.id.home_padding)

        fun onViewDetachedFromWindow() = Unit

        private val toggleList = listOf<Pair<Chip, WatchType>>(
            Pair(itemView.findViewById(R.id.home_type_watching_btt), WatchType.WATCHING),
            Pair(itemView.findViewById(R.id.home_type_completed_btt), WatchType.COMPLETED),
            Pair(itemView.findViewById(R.id.home_type_dropped_btt), WatchType.DROPPED),
            Pair(itemView.findViewById(R.id.home_type_on_hold_btt), WatchType.ONHOLD),
            Pair(itemView.findViewById(R.id.home_plan_to_watch_btt), WatchType.PLANTOWATCH),
        )

        private val toggleListHolder: ChipGroup? = itemView.findViewById(R.id.home_type_holder)

        fun bind() = Unit

        init {
            resumeRecyclerView.adapter = resumeAdapter
            bookmarkRecyclerView.setRecycledViewPool(HomeChildItemAdapter.sharedPool)
            bookmarkRecyclerView.adapter = bookmarkAdapter

            resumeRecyclerView.setLinearListLayout(
                nextLeft = R.id.nav_rail_view,
                nextRight = FOCUS_SELF
            )

            bookmarkRecyclerView.setLinearListLayout(
                nextLeft = R.id.nav_rail_view,
                nextRight = FOCUS_SELF
            )

            fixPaddingStatusbarMargin(topPadding)

            for ((chip, watch) in toggleList) {
                chip.isChecked = false
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.loadStoredData(setOf(watch))
                    }
                    // Else if all are unchecked -> Do not load data
                    else if (toggleList.all { !it.first.isChecked }) {
                        viewModel.loadStoredData(emptySet())
                    }
                }
            }

            headProfilePicCard?.isGone = isLayout(TV or EMULATOR)

            headProfilePic?.observe(viewModel.currentAccount) { currentAccount ->
                headProfilePic.loadImage(currentAccount?.image)
            }

            fun showAccountEditBox(context: Context): Boolean {
                val currentAccount = DataStoreHelper.getCurrentAccount()
                return if (currentAccount != null) {
                    showAccountEditDialog(
                        context = context,
                        account = currentAccount,
                        isNewAccount = false,
                        accountEditCallback = { accountViewModel.handleAccountUpdate(it, context) },
                        accountDeleteCallback = {
                            accountViewModel.handleAccountDelete(
                                it,
                                context
                            )
                        }
                    )
                    true
                } else false
            }

            headProfilePicCard?.setOnLongClickListener {
                showAccountEditBox(it.context)
            }

            headProfilePicCard?.setOnClickListener {
                activity?.showAccountSelectLinear()
            }

            (binding as? FragmentHomeHeadBinding)?.apply {
                homeSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        viewModel.queryTextSubmit(query)
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        viewModel.queryTextChange(newText)
                        return true
                    }
                })
            }
        }

        private fun updateResume(resumeWatching: List<SearchResponse>) {
            resumeHolder.isVisible = resumeWatching.isNotEmpty()
            resumeAdapter.submitList(resumeWatching)

            if (
                binding is FragmentHomeHeadBinding ||
                binding is FragmentHomeHeadTvBinding &&
                isLayout(EMULATOR)
            ) {
                val title = (binding as? FragmentHomeHeadBinding)?.homeWatchParentItemTitle
                    ?: (binding as? FragmentHomeHeadTvBinding)?.homeWatchParentItemTitle

                title?.setOnClickListener {
                    viewModel.popup(
                        HomeViewModel.ExpandableHomepageList(
                            HomePageList(
                                title.text.toString(),
                                resumeWatching,
                                false
                            ), 1, false
                        ),
                        deleteCallback = {
                            viewModel.deleteResumeWatching()
                        }
                    )
                }
            }
        }

        private fun updateBookmarks(data: Pair<Boolean, List<SearchResponse>>) {
            val (visible, list) = data
            bookmarkHolder.isVisible = visible
            bookmarkAdapter.submitList(list)

            if (
                binding is FragmentHomeHeadBinding ||
                binding is FragmentHomeHeadTvBinding &&
                isLayout(EMULATOR)
            ) {
                val title = (binding as? FragmentHomeHeadBinding)?.homeBookmarkParentItemTitle
                    ?: (binding as? FragmentHomeHeadTvBinding)?.homeBookmarkParentItemTitle

                title?.setOnClickListener {
                    val items = toggleList.map { it.first }.filter { it.isChecked }
                    if (items.isEmpty()) return@setOnClickListener // we don't want to show an empty dialog
                    val textSum = items
                        .mapNotNull { it.text }.joinToString()

                    viewModel.popup(
                        HomeViewModel.ExpandableHomepageList(
                            HomePageList(
                                textSum,
                                list,
                                false
                            ), 1, false
                        ), deleteCallback = {
                            viewModel.deleteBookmarks(list)
                        }
                    )
                }
            }
        }

        fun onViewAttachedToWindow() {
            itemView.apply {
                observe(viewModel.resumeWatching) {
                    updateResume(it)
                }
                observe(viewModel.bookmarks) {
                    updateBookmarks(it)
                }
                observe(viewModel.availableWatchStatusTypes) { (checked, visible) ->
                    for ((chip, watch) in toggleList) {
                        chip.apply {
                            isVisible = visible.contains(watch)
                            isChecked = checked.contains(watch)
                        }
                    }
                    toggleListHolder?.isGone = visible.isEmpty()
                }
            }
        }
    }
}
