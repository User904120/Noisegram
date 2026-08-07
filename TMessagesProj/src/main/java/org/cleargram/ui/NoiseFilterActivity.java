package org.cleargram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.cleargram.api.NoiseAction;
import org.cleargram.integration.MessageCtaButtonAction;
import org.cleargram.integration.MessageCtaButtonSettingsState;
import org.cleargram.integration.CleargramPersonalAdSuppressionPolicy;
import org.cleargram.integration.TelegramBlackListManagementGateway;
import org.cleargram.integration.TelegramHideReactionsGateway;
import org.cleargram.integration.TelegramHideChannelEndAdvertisementSettingsGateway;
import org.cleargram.integration.TelegramHideChannelPinnedMessageHeaderSettingsGateway;
import org.cleargram.integration.TelegramHideFullscreenVideoAdvertisementSettingsGateway;
import org.cleargram.integration.TelegramMessageCtaButtonSettingsGateway;
import org.cleargram.integration.TelegramDuplicateVideoSettingsGateway;
import org.cleargram.integration.TelegramNoiseBootstrap;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

/** Noise Filter navigation and Black List global-action settings. */
public final class NoiseFilterActivity extends BaseFragment {

    private static final int POS_ENABLE_ALL = 0;
    private static final int POS_HEADER_FILTERING = 1;
    private static final int POS_HIDE_REACTIONS = 2;
    private static final int POS_DUPLICATE_VIDEO = 3;
    private static final int POS_CHANNEL_END_AD = 4;
    private static final int POS_FULLSCREEN_VIDEO_AD = 5;
    private static final int POS_CHANNEL_PINNED = 6;
    private static final int POS_HEADER_RULES = 7;
    private static final int POS_WHITE_LIST = 8;
    private static final int POS_BLACK_LIST = 9;
    private static final int POS_BLACK_LIST_ACTION = 10;
    private static final int POS_HEADER_CTA = 11;
    private static final int POS_CTA_ENABLED = 12;
    private static final int POS_CTA_ACTION = 13;
    private static final int ROW_COUNT = 14;

    private boolean destroyed;
    private boolean actionLoading;
    private boolean actionUpdating;
    private boolean hideReactionsLoading;
    private boolean hideReactionsUpdating;
    private boolean hideReactionsReady;
    private boolean hideReactionsFailed;
    private boolean hideReactionsEnabled;
    private boolean duplicateVideoLoading;
    private boolean duplicateVideoUpdating;
    private boolean duplicateVideoReady;
    private boolean duplicateVideoFailed;
    private boolean duplicateVideoEnabled;
    private boolean channelEndAdvertisementLoading;
    private boolean channelEndAdvertisementUpdating;
    private boolean channelEndAdvertisementReady;
    private boolean channelEndAdvertisementFailed;
    private boolean channelEndAdvertisementEnabled;
    private boolean fullscreenVideoAdvertisementLoading;
    private boolean fullscreenVideoAdvertisementUpdating;
    private boolean fullscreenVideoAdvertisementReady;
    private boolean fullscreenVideoAdvertisementFailed;
    private boolean fullscreenVideoAdvertisementEnabled;
    private boolean channelPinnedMessageHeaderLoading;
    private boolean channelPinnedMessageHeaderUpdating;
    private boolean channelPinnedMessageHeaderReady;
    private boolean channelPinnedMessageHeaderFailed;
    private boolean channelPinnedMessageHeaderEnabled;
    private NoiseAction blackListAction;
    private final EnableAllCoordinator coordinator = new EnableAllCoordinator();
    private final EnableAllState enableAllState = new EnableAllState();
    private RulesEntryAdapter adapter;
    private TelegramBlackListManagementGateway blackListGateway;
    private TelegramHideReactionsGateway hideReactionsGateway;
    private TelegramDuplicateVideoSettingsGateway duplicateVideoSettingsGateway;
    private TelegramHideChannelEndAdvertisementSettingsGateway channelEndAdvertisementSettingsGateway;
    private TelegramHideFullscreenVideoAdvertisementSettingsGateway
            fullscreenVideoAdvertisementSettingsGateway;
    private TelegramHideChannelPinnedMessageHeaderSettingsGateway
            channelPinnedMessageHeaderSettingsGateway;
    private TelegramMessageCtaButtonSettingsGateway messageCtaButtonSettingsGateway;
    private final MessageCtaButtonUiState messageCtaButtonUiState = new MessageCtaButtonUiState();
    private final boolean personalAdSuppressionAvailable = CleargramPersonalAdSuppressionPolicy.isAvailable();
    private int messageCtaButtonRequestGeneration;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.CleargramNoiseFilter));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new RulesEntryAdapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> onRowClicked(position));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frameLayout;
        try {
            blackListGateway = TelegramNoiseBootstrap.getBlackListManagementGateway();
            hideReactionsGateway = TelegramNoiseBootstrap.getHideReactionsGateway();
            duplicateVideoSettingsGateway = TelegramNoiseBootstrap.getDuplicateVideoSettingsGateway();
            if (personalAdSuppressionAvailable) {
                channelEndAdvertisementSettingsGateway =
                        TelegramNoiseBootstrap.getHideChannelEndAdvertisementSettingsGateway();
                fullscreenVideoAdvertisementSettingsGateway =
                        TelegramNoiseBootstrap.getHideFullscreenVideoAdvertisementSettingsGateway();
            }
            channelPinnedMessageHeaderSettingsGateway =
                    TelegramNoiseBootstrap.getHideChannelPinnedMessageHeaderSettingsGateway();
            messageCtaButtonSettingsGateway =
                    TelegramNoiseBootstrap.getMessageCtaButtonSettingsGateway();
            loadAction();
            loadHideReactionsState();
            loadDuplicateVideoState();
            if (personalAdSuppressionAvailable) {
                loadChannelEndAdvertisementState();
                loadFullscreenVideoAdvertisementState();
            }
            loadChannelPinnedMessageHeaderState();
            loadMessageCtaButtonState();
        } catch (RuntimeException error) {
            FileLog.e(error);
        }
        return fragmentView;
    }

    @Override public void onFragmentDestroy() {
        destroyed = true;
        messageCtaButtonRequestGeneration++;
        coordinator.invalidate();
        super.onFragmentDestroy();
    }

    private void onRowClicked(int position) {
        if (!personalAdSuppressionAvailable && position >= POS_CHANNEL_END_AD) position += 2;
        if (position == POS_ENABLE_ALL) updateEnableAll();
        else if (position == POS_HIDE_REACTIONS) updateHideReactions();
        else if (position == POS_DUPLICATE_VIDEO) updateDuplicateVideo();
        else if (personalAdSuppressionAvailable && position == POS_CHANNEL_END_AD) updateChannelEndAdvertisement();
        else if (personalAdSuppressionAvailable && position == POS_FULLSCREEN_VIDEO_AD) updateFullscreenVideoAdvertisement();
        else if (position == POS_CHANNEL_PINNED) updateChannelPinnedMessageHeader();
        else if (position == POS_WHITE_LIST) presentFragment(NoiseFilterRulesActivity.createWhiteList());
        else if (position == POS_BLACK_LIST) presentFragment(NoiseFilterRulesActivity.createBlackList());
        else if (position == POS_CTA_ENABLED) updateMessageCtaButtonEnabled();
        else if (position == POS_CTA_ACTION) showMessageCtaButtonActionDialog();
        else if (!actionLoading && !actionUpdating && blackListAction != null) showActionDialog();
    }

    private void loadAction() {
        if (blackListGateway == null) return;
        actionLoading = true;
        adapter.notifyDataSetChanged();
        blackListGateway.getAction(new TelegramBlackListManagementGateway.Callback<NoiseAction>() {
            @Override public void onSuccess(NoiseAction result) {
                if (destroyed) return;
                actionLoading = false;
                blackListAction = result;
                adapter.notifyDataSetChanged();
            }
            @Override public void onFailure(RuntimeException error) {
                FileLog.e(error);
                if (destroyed) return;
                actionLoading = false;
                adapter.notifyDataSetChanged();
                showOperationError();
            }
        });
    }

    private void showActionDialog() {
        if (getContext() == null) return;
        showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.CleargramBlackListAction))
                .setItems(new CharSequence[]{getString(R.string.CleargramActionHide), getString(R.string.CleargramActionCollapse)},
                        (dialog, which) -> updateAction(which == 0 ? NoiseAction.HIDE : NoiseAction.COLLAPSE))
                .create());
    }

    private void updateAction(NoiseAction action) {
        if (actionUpdating || blackListGateway == null) return;
        actionUpdating = true;
        adapter.notifyDataSetChanged();
        blackListGateway.setAction(action, new TelegramBlackListManagementGateway.Callback<Void>() {
            @Override public void onSuccess(Void result) {
                if (destroyed) return;
                actionUpdating = false;
                blackListAction = action;
                adapter.notifyDataSetChanged();
            }
            @Override public void onFailure(RuntimeException error) {
                FileLog.e(error);
                if (destroyed) return;
                actionUpdating = false;
                adapter.notifyDataSetChanged();
                showOperationError();
            }
        });
    }

    private void loadHideReactionsState() {
        if (hideReactionsGateway == null) return;
        hideReactionsLoading = true;
        adapter.notifyDataSetChanged();
        hideReactionsGateway.getManagementState(new TelegramHideReactionsGateway.Callback<TelegramHideReactionsGateway.ManagementState>() {
            @Override public void onSuccess(TelegramHideReactionsGateway.ManagementState result) {
                if (destroyed) return;
                hideReactionsLoading = false;
                hideReactionsReady = result.isReady();
                hideReactionsFailed = result.isFailed();
                hideReactionsEnabled = result.isEnabled();
                adapter.notifyDataSetChanged();
                if (result.isFailed()) showOperationError();
            }
            @Override public void onFailure(RuntimeException error) {
                FileLog.e(error);
                if (destroyed) return;
                hideReactionsLoading = false;
                hideReactionsReady = false;
                hideReactionsFailed = true;
                adapter.notifyDataSetChanged();
                showOperationError();
            }
        });
    }

    private void setHideReactionsTarget(boolean target, int masterGen) {
        if (masterGen < 0 && coordinator.isUpdating()) return;
        if (hideReactionsLoading || hideReactionsUpdating || !hideReactionsReady || hideReactionsGateway == null) {
            if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
            return;
        }
        hideReactionsUpdating = true;
        adapter.notifyDataSetChanged();
        hideReactionsGateway.setHideReactionsEnabled(target, new TelegramHideReactionsGateway.Callback<Void>() {
            @Override public void onSuccess(Void result) {
                if (masterGen >= 0) {
                    if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                } else {
                    if (destroyed) return;
                }
                hideReactionsUpdating = false;
                hideReactionsEnabled = target;
                adapter.notifyDataSetChanged();
                if (masterGen >= 0) onMasterChildWriteComplete(masterGen, true);
            }
            @Override public void onFailure(RuntimeException error) {
                FileLog.e(error);
                if (masterGen >= 0) {
                    if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                } else {
                    if (destroyed) return;
                }
                hideReactionsUpdating = false;
                adapter.notifyDataSetChanged();
                if (masterGen < 0) showOperationError();
                if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
            }
        });
    }

    private void updateHideReactions() {
        setHideReactionsTarget(!hideReactionsEnabled, -1);
    }

    private void loadDuplicateVideoState() {
        if (duplicateVideoSettingsGateway == null) return;
        duplicateVideoLoading = true;
        notifyDuplicateVideoRowsChanged();
        duplicateVideoSettingsGateway.getManagementState(
                new TelegramDuplicateVideoSettingsGateway.Callback<TelegramDuplicateVideoSettingsGateway.ManagementState>() {
                    @Override public void onSuccess(TelegramDuplicateVideoSettingsGateway.ManagementState result) {
                        if (destroyed) return;
                        duplicateVideoLoading = false;
                        duplicateVideoReady = result.isReady();
                        duplicateVideoFailed = result.isFailed();
                        duplicateVideoEnabled = result.isEnabled();
                        notifyDuplicateVideoRowsChanged();
                        if (result.isFailed()) showOperationError();
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (destroyed) return;
                        duplicateVideoLoading = false;
                        duplicateVideoReady = false;
                        duplicateVideoFailed = true;
                        notifyDuplicateVideoRowsChanged();
                        showOperationError();
                    }
                });
    }

    private void setDuplicateVideoTarget(boolean target, int masterGen) {
        if (masterGen < 0 && coordinator.isUpdating()) return;
        if (duplicateVideoLoading || duplicateVideoUpdating || !duplicateVideoReady
                || duplicateVideoSettingsGateway == null) {
            if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
            return;
        }
        duplicateVideoUpdating = true;
        notifyDuplicateVideoRowsChanged();
        duplicateVideoSettingsGateway.setDuplicateVideoEnabled(target,
                new TelegramDuplicateVideoSettingsGateway.Callback<Void>() {
                    @Override public void onSuccess(Void result) {
                        if (masterGen >= 0) {
                            if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                        } else {
                            if (destroyed) return;
                        }
                        duplicateVideoUpdating = false;
                        duplicateVideoEnabled = target;
                        notifyDuplicateVideoRowsChanged();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, true);
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (masterGen >= 0) {
                            if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                        } else {
                            if (destroyed) return;
                        }
                        duplicateVideoUpdating = false;
                        notifyDuplicateVideoRowsChanged();
                        if (masterGen < 0) showOperationError();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
                    }
                });
    }

    private void updateDuplicateVideo() {
        setDuplicateVideoTarget(!duplicateVideoEnabled, -1);
    }

    private void notifyDuplicateVideoRowsChanged() {
        adapter.notifyItemChanged(POS_DUPLICATE_VIDEO);
        adapter.notifyItemChanged(POS_ENABLE_ALL);
    }

    private void loadChannelEndAdvertisementState() {
        if (channelEndAdvertisementSettingsGateway == null) return;
        channelEndAdvertisementLoading = true;
        adapter.notifyDataSetChanged();
        channelEndAdvertisementSettingsGateway.getManagementState(
                new TelegramHideChannelEndAdvertisementSettingsGateway.Callback<TelegramHideChannelEndAdvertisementSettingsGateway.ManagementState>() {
                    @Override public void onSuccess(TelegramHideChannelEndAdvertisementSettingsGateway.ManagementState result) {
                        if (destroyed) return;
                        channelEndAdvertisementLoading = false;
                        channelEndAdvertisementReady = result.isReady();
                        channelEndAdvertisementFailed = result.isFailed();
                        channelEndAdvertisementEnabled = result.isEnabled();
                        adapter.notifyDataSetChanged();
                        if (result.isFailed()) showOperationError();
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (destroyed) return;
                        channelEndAdvertisementLoading = false;
                        channelEndAdvertisementReady = false;
                        channelEndAdvertisementFailed = true;
                        adapter.notifyDataSetChanged();
                        showOperationError();
                    }
                });
    }

    private void setChannelEndAdvertisementTarget(boolean target, int masterGen) {
        if (masterGen < 0 && coordinator.isUpdating()) return;
        if (channelEndAdvertisementLoading || channelEndAdvertisementUpdating
                || !channelEndAdvertisementReady || channelEndAdvertisementSettingsGateway == null) {
            if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
            return;
        }
        channelEndAdvertisementUpdating = true;
        adapter.notifyDataSetChanged();
        channelEndAdvertisementSettingsGateway.setHideChannelEndAdvertisementEnabled(target,
                new TelegramHideChannelEndAdvertisementSettingsGateway.Callback<Void>() {
                    @Override public void onSuccess(Void result) {
                        if (masterGen >= 0) {
                            if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                        } else {
                            if (destroyed) return;
                        }
                        channelEndAdvertisementUpdating = false;
                        channelEndAdvertisementEnabled = target;
                        adapter.notifyDataSetChanged();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, true);
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (masterGen >= 0) {
                            if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                        } else {
                            if (destroyed) return;
                        }
                        channelEndAdvertisementUpdating = false;
                        adapter.notifyDataSetChanged();
                        if (masterGen < 0) showOperationError();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
                    }
        });
    }

    private void updateChannelEndAdvertisement() {
        setChannelEndAdvertisementTarget(!channelEndAdvertisementEnabled, -1);
    }

    private void loadFullscreenVideoAdvertisementState() {
        if (fullscreenVideoAdvertisementSettingsGateway == null) return;
        fullscreenVideoAdvertisementLoading = true;
        adapter.notifyDataSetChanged();
        fullscreenVideoAdvertisementSettingsGateway.getManagementState(
                new TelegramHideFullscreenVideoAdvertisementSettingsGateway.Callback<TelegramHideFullscreenVideoAdvertisementSettingsGateway.ManagementState>() {
                    @Override public void onSuccess(TelegramHideFullscreenVideoAdvertisementSettingsGateway.ManagementState result) {
                        if (destroyed) return;
                        fullscreenVideoAdvertisementLoading = false;
                        fullscreenVideoAdvertisementReady = result.isReady();
                        fullscreenVideoAdvertisementFailed = result.isFailed();
                        fullscreenVideoAdvertisementEnabled = result.isEnabled();
                        adapter.notifyDataSetChanged();
                        if (result.isFailed()) showOperationError();
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (destroyed) return;
                        fullscreenVideoAdvertisementLoading = false;
                        fullscreenVideoAdvertisementReady = false;
                        fullscreenVideoAdvertisementFailed = true;
                        adapter.notifyDataSetChanged();
                        showOperationError();
                    }
                });
    }

    private void setFullscreenVideoAdvertisementTarget(boolean target, int masterGen) {
        if (masterGen < 0 && coordinator.isUpdating()) return;
        if (fullscreenVideoAdvertisementLoading || fullscreenVideoAdvertisementUpdating
                || !fullscreenVideoAdvertisementReady
                || fullscreenVideoAdvertisementSettingsGateway == null) {
            if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
            return;
        }
        fullscreenVideoAdvertisementUpdating = true;
        adapter.notifyDataSetChanged();
        fullscreenVideoAdvertisementSettingsGateway.setHideFullscreenVideoAdvertisementEnabled(target,
                new TelegramHideFullscreenVideoAdvertisementSettingsGateway.Callback<Void>() {
                    @Override public void onSuccess(Void result) {
                        if (masterGen >= 0) {
                            if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                        } else {
                            if (destroyed) return;
                        }
                        fullscreenVideoAdvertisementUpdating = false;
                        fullscreenVideoAdvertisementEnabled = target;
                        adapter.notifyDataSetChanged();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, true);
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (masterGen >= 0) {
                            if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                        } else {
                            if (destroyed) return;
                        }
                        fullscreenVideoAdvertisementUpdating = false;
                        adapter.notifyDataSetChanged();
                        if (masterGen < 0) showOperationError();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
                    }
                });
    }

    private void updateFullscreenVideoAdvertisement() {
        setFullscreenVideoAdvertisementTarget(!fullscreenVideoAdvertisementEnabled, -1);
    }

    private void loadChannelPinnedMessageHeaderState() {
        if (channelPinnedMessageHeaderSettingsGateway == null) return;
        channelPinnedMessageHeaderLoading = true;
        adapter.notifyDataSetChanged();
        channelPinnedMessageHeaderSettingsGateway.getManagementState(
                new TelegramHideChannelPinnedMessageHeaderSettingsGateway.Callback<TelegramHideChannelPinnedMessageHeaderSettingsGateway.ManagementState>() {
                    @Override public void onSuccess(TelegramHideChannelPinnedMessageHeaderSettingsGateway.ManagementState result) {
                        if (destroyed) return;
                        channelPinnedMessageHeaderLoading = false;
                        channelPinnedMessageHeaderReady = result.isReady();
                        channelPinnedMessageHeaderFailed = result.isFailed();
                        channelPinnedMessageHeaderEnabled = result.isEnabled();
                        adapter.notifyDataSetChanged();
                        if (result.isFailed()) showOperationError();
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (destroyed) return;
                        channelPinnedMessageHeaderLoading = false;
                        channelPinnedMessageHeaderReady = false;
                        channelPinnedMessageHeaderFailed = true;
                        adapter.notifyDataSetChanged();
                        showOperationError();
                    }
                });
    }

    private void setChannelPinnedMessageHeaderTarget(boolean target, int masterGen) {
        if (masterGen < 0 && coordinator.isUpdating()) return;
        if (channelPinnedMessageHeaderLoading || channelPinnedMessageHeaderUpdating
                || !channelPinnedMessageHeaderReady || channelPinnedMessageHeaderSettingsGateway == null) {
            if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
            return;
        }
        channelPinnedMessageHeaderUpdating = true;
        adapter.notifyDataSetChanged();
        channelPinnedMessageHeaderSettingsGateway.setHideChannelPinnedMessageHeaderEnabled(target,
                new TelegramHideChannelPinnedMessageHeaderSettingsGateway.Callback<Void>() {
                    @Override public void onSuccess(Void result) {
                        if (masterGen >= 0) {
                            if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                        } else {
                            if (destroyed) return;
                        }
                        channelPinnedMessageHeaderUpdating = false;
                        channelPinnedMessageHeaderEnabled = target;
                        adapter.notifyDataSetChanged();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, true);
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (masterGen >= 0) {
                            if (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration()) return;
                        } else {
                            if (destroyed) return;
                        }
                        channelPinnedMessageHeaderUpdating = false;
                        adapter.notifyDataSetChanged();
                        if (masterGen < 0) showOperationError();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
                    }
                });
    }

    private void updateChannelPinnedMessageHeader() {
        setChannelPinnedMessageHeaderTarget(!channelPinnedMessageHeaderEnabled, -1);
    }

    private void loadMessageCtaButtonState() {
        if (messageCtaButtonSettingsGateway == null) return;
        int generation = ++messageCtaButtonRequestGeneration;
        messageCtaButtonUiState.beginLoad();
        adapter.notifyDataSetChanged();
        messageCtaButtonSettingsGateway.load(
                new TelegramMessageCtaButtonSettingsGateway.Callback<MessageCtaButtonSettingsState>() {
                    @Override public void onSuccess(MessageCtaButtonSettingsState result) {
                        if (!isCurrentMessageCtaButtonRequest(generation)) return;
                        messageCtaButtonUiState.completeLoad(result);
                        adapter.notifyDataSetChanged();
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (!isCurrentMessageCtaButtonRequest(generation)) return;
                        messageCtaButtonUiState.failLoad();
                        adapter.notifyDataSetChanged();
                        showOperationError();
                    }
                });
    }

    private void setMessageCtaButtonEnabledTarget(boolean target, int masterGen) {
        if (masterGen < 0 && coordinator.isUpdating()) return;
        if (!messageCtaButtonUiState.canChangeEnabled()
                || messageCtaButtonSettingsGateway == null) {
            if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
            return;
        }
        int generation = ++messageCtaButtonRequestGeneration;
        messageCtaButtonUiState.beginEnabledWrite();
        adapter.notifyDataSetChanged();
        messageCtaButtonSettingsGateway.setEnabled(target,
                new TelegramMessageCtaButtonSettingsGateway.Callback<MessageCtaButtonSettingsState>() {
                    @Override public void onSuccess(MessageCtaButtonSettingsState result) {
                        if (!isCurrentMessageCtaButtonRequest(generation)) return;
                        if (masterGen >= 0
                                && (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration())) return;
                        messageCtaButtonUiState.completeWrite(result);
                        adapter.notifyDataSetChanged();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, true);
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (!isCurrentMessageCtaButtonRequest(generation)) return;
                        if (masterGen >= 0
                                && (destroyed || !coordinator.isUpdating() || masterGen != coordinator.getGeneration())) return;
                        messageCtaButtonUiState.failWrite();
                        adapter.notifyDataSetChanged();
                        if (masterGen < 0) showOperationError();
                        if (masterGen >= 0) onMasterChildWriteComplete(masterGen, false);
                    }
                });
    }

    private void updateMessageCtaButtonEnabled() {
        boolean currentEnabled = messageCtaButtonUiState.getLoadedState() != null
                && messageCtaButtonUiState.getLoadedState().isEnabled();
        setMessageCtaButtonEnabledTarget(!currentEnabled, -1);
    }

    private void showMessageCtaButtonActionDialog() {
        if (!messageCtaButtonUiState.canChangeAction() || getContext() == null) return;
        showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.CleargramMessageCtaButtonAction))
                .setItems(new CharSequence[]{
                                getString(R.string.CleargramMessageCtaButtonActionHide),
                                getString(R.string.CleargramMessageCtaButtonActionCollapse)},
                        (dialog, which) -> {
                            dialog.dismiss();
                            updateMessageCtaButtonAction(which == 0
                                    ? MessageCtaButtonAction.HIDE
                                    : MessageCtaButtonAction.COLLAPSE);
                        })
                .create());
    }

    private void updateMessageCtaButtonAction(MessageCtaButtonAction action) {
        if (action == null || !messageCtaButtonUiState.canChangeAction()
                || messageCtaButtonSettingsGateway == null) return;
        int generation = ++messageCtaButtonRequestGeneration;
        messageCtaButtonUiState.beginActionWrite();
        adapter.notifyDataSetChanged();
        messageCtaButtonSettingsGateway.setAction(action,
                new TelegramMessageCtaButtonSettingsGateway.Callback<MessageCtaButtonSettingsState>() {
                    @Override public void onSuccess(MessageCtaButtonSettingsState result) {
                        if (!isCurrentMessageCtaButtonRequest(generation)) return;
                        messageCtaButtonUiState.completeWrite(result);
                        adapter.notifyDataSetChanged();
                    }

                    @Override public void onFailure(RuntimeException error) {
                        FileLog.e(error);
                        if (!isCurrentMessageCtaButtonRequest(generation)) return;
                        messageCtaButtonUiState.failWrite();
                        adapter.notifyDataSetChanged();
                        showOperationError();
                    }
                });
    }

    private boolean isCurrentMessageCtaButtonRequest(int generation) {
        return !destroyed && generation == messageCtaButtonRequestGeneration;
    }

    private void showOperationError() {
        if (getContext() == null) return;
        showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                .setMessage(getString(R.string.CleargramOperationFailed))
                .setPositiveButton(getString(R.string.OK), null).create());
    }

    // --- Enable All (master) ---

    private void syncEnableAllState() {
        enableAllState.includePersonalAdSuppression = personalAdSuppressionAvailable;
        enableAllState.hideReactionsEnabled = hideReactionsEnabled;
        enableAllState.duplicateVideoEnabled = duplicateVideoEnabled;
        enableAllState.channelEndAdvertisementEnabled = !personalAdSuppressionAvailable || channelEndAdvertisementEnabled;
        enableAllState.fullscreenVideoAdvertisementEnabled = !personalAdSuppressionAvailable || fullscreenVideoAdvertisementEnabled;
        enableAllState.channelPinnedMessageHeaderEnabled = channelPinnedMessageHeaderEnabled;
        enableAllState.ctaEnabled = messageCtaButtonUiState.getLoadedState() != null
                && messageCtaButtonUiState.getLoadedState().isEnabled();

        enableAllState.hideReactionsLoading = hideReactionsLoading;
        enableAllState.hideReactionsUpdating = hideReactionsUpdating;
        enableAllState.hideReactionsReady = hideReactionsReady;
        enableAllState.hideReactionsFailed = hideReactionsFailed;

        enableAllState.duplicateVideoLoading = duplicateVideoLoading;
        enableAllState.duplicateVideoUpdating = duplicateVideoUpdating;
        enableAllState.duplicateVideoReady = duplicateVideoReady;
        enableAllState.duplicateVideoFailed = duplicateVideoFailed;

        enableAllState.channelEndAdvertisementLoading = personalAdSuppressionAvailable && channelEndAdvertisementLoading;
        enableAllState.channelEndAdvertisementUpdating = personalAdSuppressionAvailable && channelEndAdvertisementUpdating;
        enableAllState.channelEndAdvertisementReady = !personalAdSuppressionAvailable || channelEndAdvertisementReady;
        enableAllState.channelEndAdvertisementFailed = personalAdSuppressionAvailable && channelEndAdvertisementFailed;

        enableAllState.fullscreenVideoAdvertisementLoading = personalAdSuppressionAvailable && fullscreenVideoAdvertisementLoading;
        enableAllState.fullscreenVideoAdvertisementUpdating = personalAdSuppressionAvailable && fullscreenVideoAdvertisementUpdating;
        enableAllState.fullscreenVideoAdvertisementReady = !personalAdSuppressionAvailable || fullscreenVideoAdvertisementReady;
        enableAllState.fullscreenVideoAdvertisementFailed = personalAdSuppressionAvailable && fullscreenVideoAdvertisementFailed;

        enableAllState.channelPinnedMessageHeaderLoading = channelPinnedMessageHeaderLoading;
        enableAllState.channelPinnedMessageHeaderUpdating = channelPinnedMessageHeaderUpdating;
        enableAllState.channelPinnedMessageHeaderReady = channelPinnedMessageHeaderReady;
        enableAllState.channelPinnedMessageHeaderFailed = channelPinnedMessageHeaderFailed;

        enableAllState.ctaLoading = messageCtaButtonUiState.isLoading();
        enableAllState.ctaWriting = messageCtaButtonUiState.isWriting();
        enableAllState.ctaReady = messageCtaButtonUiState.getLoadedState() != null;

        enableAllState.masterUpdating = coordinator.isUpdating();
    }

    private boolean isMasterChecked() {
        syncEnableAllState();
        return enableAllState.computeChecked();
    }

    private boolean isMasterRowEnabled() {
        if (hideReactionsGateway == null || duplicateVideoSettingsGateway == null
                || (personalAdSuppressionAvailable && (channelEndAdvertisementSettingsGateway == null
                || fullscreenVideoAdvertisementSettingsGateway == null))
                || channelPinnedMessageHeaderSettingsGateway == null
                || messageCtaButtonSettingsGateway == null) return false;
        syncEnableAllState();
        return enableAllState.computeEnabled();
    }

    static boolean isDuplicateVideoRowInteractive(
            boolean loading, boolean updating, boolean ready, boolean failed, boolean masterUpdating
    ) {
        return !masterUpdating && EnableAllState.isChildReady(loading, updating, ready, failed);
    }

    private boolean isAnyChildLoading() {
        return hideReactionsLoading || duplicateVideoLoading || (personalAdSuppressionAvailable && (channelEndAdvertisementLoading
                || fullscreenVideoAdvertisementLoading)) || channelPinnedMessageHeaderLoading
                || messageCtaButtonUiState.isLoading();
    }

    private boolean isAnyChildWriting() {
        return hideReactionsUpdating || duplicateVideoUpdating || (personalAdSuppressionAvailable && (channelEndAdvertisementUpdating
                || fullscreenVideoAdvertisementUpdating)) || channelPinnedMessageHeaderUpdating
                || messageCtaButtonUiState.isWriting();
    }

    private boolean isAnyChildFailed() {
        if (!hideReactionsReady || hideReactionsFailed) return true;
        if (!duplicateVideoReady || duplicateVideoFailed) return true;
        if (personalAdSuppressionAvailable && (!channelEndAdvertisementReady || channelEndAdvertisementFailed)) return true;
        if (personalAdSuppressionAvailable && (!fullscreenVideoAdvertisementReady || fullscreenVideoAdvertisementFailed)) return true;
        if (!channelPinnedMessageHeaderReady || channelPinnedMessageHeaderFailed) return true;
        if (messageCtaButtonUiState.getLoadedState() == null) return true;
        return false;
    }

    private void updateEnableAll() {
        if (coordinator.isUpdating()) return;
        if (hideReactionsGateway == null || duplicateVideoSettingsGateway == null
                || (personalAdSuppressionAvailable && (channelEndAdvertisementSettingsGateway == null
                || fullscreenVideoAdvertisementSettingsGateway == null))
                || channelPinnedMessageHeaderSettingsGateway == null
                || messageCtaButtonSettingsGateway == null) return;
        if (isAnyChildLoading() || isAnyChildFailed() || isAnyChildWriting()) return;

        syncEnableAllState();
        boolean target = !enableAllState.computeChecked();
        int writeCount = enableAllState.computeWriteCount(target);

        if (!coordinator.start(writeCount)) return;
        adapter.notifyDataSetChanged();

        if (writeCount == 0) {
            adapter.notifyDataSetChanged();
            return;
        }

        int gen = coordinator.getGeneration();
        if (hideReactionsEnabled != target) setHideReactionsTarget(target, gen);
        if (duplicateVideoEnabled != target) setDuplicateVideoTarget(target, gen);
        if (personalAdSuppressionAvailable && channelEndAdvertisementEnabled != target) setChannelEndAdvertisementTarget(target, gen);
        if (personalAdSuppressionAvailable && fullscreenVideoAdvertisementEnabled != target) setFullscreenVideoAdvertisementTarget(target, gen);
        if (channelPinnedMessageHeaderEnabled != target) setChannelPinnedMessageHeaderTarget(target, gen);
        boolean ctaEnabled = messageCtaButtonUiState.getLoadedState() != null
                && messageCtaButtonUiState.getLoadedState().isEnabled();
        if (ctaEnabled != target) setMessageCtaButtonEnabledTarget(target, gen);
    }

    private void onMasterChildWriteComplete(int generation, boolean success) {
        EnableAllCoordinator.CallbackResult result = coordinator.onCallback(generation, destroyed, success);
        if (result == EnableAllCoordinator.CallbackResult.PENDING) return;
        if (result == EnableAllCoordinator.CallbackResult.IGNORED) return;
        adapter.notifyDataSetChanged();
        if (result == EnableAllCoordinator.CallbackResult.COMPLETED_ERROR) showOperationError();
    }

    // --- Adapter ---

    private final class RulesEntryAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;
        RulesEntryAdapter(Context context) { this.context = context; }
        @Override public int getItemCount() { return personalAdSuppressionAvailable ? ROW_COUNT : ROW_COUNT - 2; }
        private int logicalPosition(int position) {
            if (!personalAdSuppressionAvailable && position >= POS_CHANNEL_END_AD) {
                return position + 2;
            }
            return position;
        }
        @Override public int getItemViewType(int position) {
            position = logicalPosition(position);
            if (position == POS_HEADER_FILTERING || position == POS_HEADER_RULES || position == POS_HEADER_CTA) return 0;
            if (position == POS_WHITE_LIST || position == POS_BLACK_LIST
                    || position == POS_BLACK_LIST_ACTION || position == POS_CTA_ACTION) return 1;
            return 2;
        }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = logicalPosition(holder.getAdapterPosition());
            if (position == POS_ENABLE_ALL) return isMasterRowEnabled();
            if (position == POS_HEADER_FILTERING || position == POS_HEADER_RULES || position == POS_HEADER_CTA) return false;
            if (position == POS_HIDE_REACTIONS) return isHideReactionsRowEnabled();
            if (position == POS_DUPLICATE_VIDEO) return isDuplicateVideoRowEnabled();
            if (position == POS_CHANNEL_END_AD) return isChannelEndAdvertisementRowEnabled();
            if (position == POS_FULLSCREEN_VIDEO_AD) return isFullscreenVideoAdvertisementRowEnabled();
            if (position == POS_CHANNEL_PINNED) return isChannelPinnedMessageHeaderRowEnabled();
            if (position == POS_BLACK_LIST_ACTION) return !actionLoading && !actionUpdating && blackListAction != null;
            if (position == POS_CTA_ENABLED) return !coordinator.isUpdating() && messageCtaButtonUiState.canChangeEnabled();
            if (position == POS_CTA_ACTION) return messageCtaButtonUiState.canChangeAction();
            return true;
        }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            View cell;
            if (type == 0) {
                cell = new HeaderCell(context);
            } else if (type == 1) {
                cell = new TextSettingsCell(context);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else {
                cell = new TextCheckCell(context);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
            return new RecyclerListView.Holder(cell);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            position = logicalPosition(position);
            if (position == POS_ENABLE_ALL) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck(getString(R.string.CleargramEnableAll), isMasterChecked(), false);
                cell.setEnabled(isMasterRowEnabled());
                return;
            }
            if (position == POS_HEADER_FILTERING || position == POS_HEADER_RULES || position == POS_HEADER_CTA) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                cell.setText(getString(position == POS_HEADER_FILTERING ? R.string.CleargramMessageFiltering
                        : position == POS_HEADER_RULES ? R.string.CleargramFilteringRules
                        : R.string.CleargramCtaMessages));
                return;
            }
            if (position == POS_HIDE_REACTIONS) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck(getString(R.string.CleargramHideReactions), hideReactionsEnabled, false);
                cell.setEnabled(isHideReactionsRowEnabled());
                return;
            }
            if (position == POS_DUPLICATE_VIDEO) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck(getString(R.string.CleargramHideDuplicateVideos), duplicateVideoEnabled, false);
                cell.setEnabled(isDuplicateVideoRowEnabled());
                return;
            }
            if (position == POS_CHANNEL_END_AD) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndValueAndCheck(
                        getString(R.string.CleargramHideChannelEndAdvertisement),
                        getString(R.string.CleargramHideChannelEndAdvertisementSummary),
                        channelEndAdvertisementEnabled, true, false);
                cell.setEnabled(isChannelEndAdvertisementRowEnabled());
                return;
            }
            if (position == POS_FULLSCREEN_VIDEO_AD) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndValueAndCheck(
                        getString(R.string.CleargramHideFullscreenVideoAdvertisement),
                        getString(R.string.CleargramHideFullscreenVideoAdvertisementSummary),
                        fullscreenVideoAdvertisementEnabled, true, false);
                cell.setEnabled(isFullscreenVideoAdvertisementRowEnabled());
                return;
            }
            if (position == POS_CHANNEL_PINNED) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck(
                        getString(R.string.CleargramHideChannelPinnedMessageHeader),
                        channelPinnedMessageHeaderEnabled, false);
                cell.setEnabled(isChannelPinnedMessageHeaderRowEnabled());
                return;
            }
            if (position == POS_CTA_ENABLED) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                MessageCtaButtonSettingsState state = messageCtaButtonUiState.getLoadedState();
                cell.setTextAndCheck(getString(R.string.CleargramMessageCtaButton),
                        state != null && state.isEnabled(), false);
                cell.setEnabled(!coordinator.isUpdating() && messageCtaButtonUiState.canChangeEnabled());
                return;
            }
            TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            if (position == POS_WHITE_LIST) cell.setText(getString(R.string.CleargramWhiteList), true);
            else if (position == POS_BLACK_LIST) cell.setText(getString(R.string.CleargramBlackList), true);
            else if (position == POS_BLACK_LIST_ACTION) cell.setTextAndValue(getString(R.string.CleargramBlackListAction), actionLoading ? "" : actionLabel(), true);
            else {
                cell.setTextAndValue(getString(R.string.CleargramMessageCtaButtonAction),
                        messageCtaButtonActionLabel(), true);
                cell.setEnabled(messageCtaButtonUiState.canChangeAction());
            }
        }
        private String actionLabel() {
            return blackListAction == NoiseAction.HIDE ? getString(R.string.CleargramActionHide)
                    : blackListAction == NoiseAction.COLLAPSE ? getString(R.string.CleargramActionCollapse) : "";
        }

        private String messageCtaButtonActionLabel() {
            MessageCtaButtonSettingsState state = messageCtaButtonUiState.getLoadedState();
            if (state == null) return "";
            return state.getAction() == MessageCtaButtonAction.HIDE
                    ? getString(R.string.CleargramMessageCtaButtonActionHide)
                    : state.getAction() == MessageCtaButtonAction.COLLAPSE
                    ? getString(R.string.CleargramMessageCtaButtonActionCollapse) : "";
        }

        private boolean isHideReactionsRowEnabled() {
            return !coordinator.isUpdating() && EnableAllState.isChildReady(
                    hideReactionsLoading, hideReactionsUpdating, hideReactionsReady, hideReactionsFailed);
        }

        private boolean isDuplicateVideoRowEnabled() {
            return isDuplicateVideoRowInteractive(duplicateVideoLoading, duplicateVideoUpdating,
                    duplicateVideoReady, duplicateVideoFailed, coordinator.isUpdating());
        }

        private boolean isChannelEndAdvertisementRowEnabled() {
            return !coordinator.isUpdating() && EnableAllState.isChildReady(
                    channelEndAdvertisementLoading, channelEndAdvertisementUpdating,
                    channelEndAdvertisementReady, channelEndAdvertisementFailed);
        }

        private boolean isFullscreenVideoAdvertisementRowEnabled() {
            return !coordinator.isUpdating() && EnableAllState.isChildReady(
                    fullscreenVideoAdvertisementLoading, fullscreenVideoAdvertisementUpdating,
                    fullscreenVideoAdvertisementReady, fullscreenVideoAdvertisementFailed);
        }

        private boolean isChannelPinnedMessageHeaderRowEnabled() {
            return !coordinator.isUpdating() && EnableAllState.isChildReady(
                    channelPinnedMessageHeaderLoading, channelPinnedMessageHeaderUpdating,
                    channelPinnedMessageHeaderReady, channelPinnedMessageHeaderFailed);
        }
    }

    static final class EnableAllState {
        boolean includePersonalAdSuppression;
        boolean hideReactionsEnabled;
        boolean duplicateVideoEnabled;
        boolean channelEndAdvertisementEnabled;
        boolean fullscreenVideoAdvertisementEnabled;
        boolean channelPinnedMessageHeaderEnabled;
        boolean ctaEnabled;

        boolean hideReactionsLoading;
        boolean hideReactionsUpdating;
        boolean hideReactionsReady;
        boolean hideReactionsFailed;

        boolean duplicateVideoLoading;
        boolean duplicateVideoUpdating;
        boolean duplicateVideoReady;
        boolean duplicateVideoFailed;

        boolean channelEndAdvertisementLoading;
        boolean channelEndAdvertisementUpdating;
        boolean channelEndAdvertisementReady;
        boolean channelEndAdvertisementFailed;

        boolean fullscreenVideoAdvertisementLoading;
        boolean fullscreenVideoAdvertisementUpdating;
        boolean fullscreenVideoAdvertisementReady;
        boolean fullscreenVideoAdvertisementFailed;

        boolean channelPinnedMessageHeaderLoading;
        boolean channelPinnedMessageHeaderUpdating;
        boolean channelPinnedMessageHeaderReady;
        boolean channelPinnedMessageHeaderFailed;

        boolean ctaLoading;
        boolean ctaWriting;
        boolean ctaReady;

        boolean masterUpdating;

        static boolean isChildReady(boolean loading, boolean updating, boolean ready, boolean failed) {
            return !loading && !updating && ready && !failed;
        }

        boolean computeChecked() {
            return hideReactionsEnabled && duplicateVideoEnabled
                    && (!includePersonalAdSuppression || (channelEndAdvertisementEnabled && fullscreenVideoAdvertisementEnabled))
                    && channelPinnedMessageHeaderEnabled && ctaEnabled;
        }

        boolean computeEnabled() {
            if (!isChildReady(hideReactionsLoading, hideReactionsUpdating,
                    hideReactionsReady, hideReactionsFailed)) return false;
            if (!isChildReady(duplicateVideoLoading, duplicateVideoUpdating,
                    duplicateVideoReady, duplicateVideoFailed)) return false;
            if (includePersonalAdSuppression && !isChildReady(channelEndAdvertisementLoading, channelEndAdvertisementUpdating,
                    channelEndAdvertisementReady, channelEndAdvertisementFailed)) return false;
            if (includePersonalAdSuppression && !isChildReady(fullscreenVideoAdvertisementLoading, fullscreenVideoAdvertisementUpdating,
                    fullscreenVideoAdvertisementReady, fullscreenVideoAdvertisementFailed)) return false;
            if (!isChildReady(channelPinnedMessageHeaderLoading, channelPinnedMessageHeaderUpdating,
                    channelPinnedMessageHeaderReady, channelPinnedMessageHeaderFailed)) return false;
            if (ctaLoading || ctaWriting || !ctaReady) return false;
            if (masterUpdating) return false;
            return true;
        }

        int computeWriteCount(boolean target) {
            int count = 0;
            if (hideReactionsEnabled != target) count++;
            if (duplicateVideoEnabled != target) count++;
            if (includePersonalAdSuppression && channelEndAdvertisementEnabled != target) count++;
            if (includePersonalAdSuppression && fullscreenVideoAdvertisementEnabled != target) count++;
            if (channelPinnedMessageHeaderEnabled != target) count++;
            if (ctaEnabled != target) count++;
            return count;
        }
    }

    static final class EnableAllCoordinator {

        enum CallbackResult {
            IGNORED,
            PENDING,
            COMPLETED_OK,
            COMPLETED_ERROR
        }

        private boolean updating;
        private int generation;
        private int pendingWrites;
        private boolean hadError;

        boolean isUpdating() { return updating; }

        int getGeneration() { return generation; }

        int getPendingWrites() { return pendingWrites; }

        boolean hadError() { return hadError; }

        boolean start(int writeCount) {
            if (updating) return false;
            generation++;
            hadError = false;
            pendingWrites = writeCount;
            updating = writeCount > 0;
            return true;
        }

        CallbackResult onCallback(int callbackGeneration, boolean destroyed, boolean success) {
            if (destroyed || !updating || callbackGeneration != generation) {
                return CallbackResult.IGNORED;
            }
            pendingWrites--;
            if (!success) hadError = true;
            if (pendingWrites == 0) {
                updating = false;
                return hadError ? CallbackResult.COMPLETED_ERROR : CallbackResult.COMPLETED_OK;
            }
            return CallbackResult.PENDING;
        }

        void invalidate() {
            updating = false;
            generation++;
        }
    }

    static final class MessageCtaButtonUiState {
        private MessageCtaButtonSettingsState loadedState;
        private boolean loading;
        private boolean writingEnabled;
        private boolean writingAction;

        void beginLoad() {
            loading = true;
            loadedState = null;
            writingEnabled = false;
            writingAction = false;
        }

        void completeLoad(MessageCtaButtonSettingsState state) {
            loading = false;
            loadedState = state;
        }

        void failLoad() {
            loading = false;
            loadedState = null;
        }

        void beginEnabledWrite() {
            writingEnabled = true;
        }

        void beginActionWrite() {
            writingAction = true;
        }

        void completeWrite(MessageCtaButtonSettingsState state) {
            writingEnabled = false;
            writingAction = false;
            loadedState = state;
        }

        void failWrite() {
            writingEnabled = false;
            writingAction = false;
        }

        MessageCtaButtonSettingsState getLoadedState() {
            return loadedState;
        }

        boolean isLoading() {
            return loading;
        }

        boolean isWriting() {
            return writingEnabled || writingAction;
        }

        boolean canChangeEnabled() {
            return !loading && !writingEnabled && !writingAction && loadedState != null;
        }

        boolean canChangeAction() {
            return canChangeEnabled() && loadedState.isEnabled();
        }
    }
}
