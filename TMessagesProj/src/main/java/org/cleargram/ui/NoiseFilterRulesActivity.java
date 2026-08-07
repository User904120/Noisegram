package org.cleargram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.cleargram.api.BlackListRuleSnapshot;
import org.cleargram.api.WhiteListRuleSnapshot;
import org.cleargram.integration.TelegramBlackListManagementGateway;
import org.cleargram.integration.TelegramNoiseBootstrap;
import org.cleargram.integration.TelegramWhiteListManagementGateway;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

/** One list screen, explicitly configured for either White List or Black List. */
public final class NoiseFilterRulesActivity extends BaseFragment {
    private static final String ARG_MODE = "mode";
    private static final int MODE_WHITE_LIST = 1;
    private static final int MODE_BLACK_LIST = 2;
    private static final int MENU_ADD = 1;

    private final ArrayList<String> rules = new ArrayList<>();
    private boolean destroyed;
    private boolean operationInProgress;
    private int mode;
    private TelegramWhiteListManagementGateway whiteGateway;
    private TelegramBlackListManagementGateway blackGateway;
    private RulesAdapter adapter;
    private EmptyTextProgressView emptyView;

    public NoiseFilterRulesActivity() { }

    private NoiseFilterRulesActivity(Bundle arguments) { super(arguments); }

    public static NoiseFilterRulesActivity createWhiteList() { return create(MODE_WHITE_LIST); }
    public static NoiseFilterRulesActivity createBlackList() { return create(MODE_BLACK_LIST); }
    private static NoiseFilterRulesActivity create(int mode) {
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_MODE, mode);
        return new NoiseFilterRulesActivity(arguments);
    }

    @Override public View createView(Context context) {
        mode = getArguments() == null ? 0 : getArguments().getInt(ARG_MODE);
        if (mode != MODE_WHITE_LIST && mode != MODE_BLACK_LIST) {
            finishFragment();
            return new FrameLayout(context);
        }
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(mode == MODE_WHITE_LIST ? R.string.CleargramWhiteList : R.string.CleargramBlackList));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); else if (id == MENU_ADD && !operationInProgress) showAddDialog(); }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_ADD, R.drawable.msg_add).setContentDescription(getString(R.string.CleargramAddRule));
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        emptyView = new EmptyTextProgressView(context);
        emptyView.setText(getString(R.string.CleargramNoRules));
        frame.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        adapter = new RulesAdapter(context);
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);
        frame.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frame;
        try {
            if (mode == MODE_WHITE_LIST) whiteGateway = TelegramNoiseBootstrap.getWhiteListManagementGateway();
            else blackGateway = TelegramNoiseBootstrap.getBlackListManagementGateway();
            loadRules();
        } catch (RuntimeException error) { showLoadFailure(error); }
        return fragmentView;
    }

    @Override public void onFragmentDestroy() { destroyed = true; super.onFragmentDestroy(); }

    private void loadRules() {
        if (destroyed) return;
        emptyView.showProgress();
        if (mode == MODE_WHITE_LIST) {
            whiteGateway.getRules(new TelegramWhiteListManagementGateway.Callback<List<WhiteListRuleSnapshot>>() {
                @Override public void onSuccess(List<WhiteListRuleSnapshot> result) { applyWhite(result); }
                @Override public void onFailure(RuntimeException error) { showLoadFailure(error); }
            });
        } else {
            blackGateway.getRules(new TelegramBlackListManagementGateway.Callback<List<BlackListRuleSnapshot>>() {
                @Override public void onSuccess(List<BlackListRuleSnapshot> result) { applyBlack(result); }
                @Override public void onFailure(RuntimeException error) { showLoadFailure(error); }
            });
        }
    }

    private void applyWhite(List<WhiteListRuleSnapshot> result) { if (destroyed) return; rules.clear(); for (WhiteListRuleSnapshot rule : result) rules.add(rule.getCanonicalPattern()); showRules(); }
    private void applyBlack(List<BlackListRuleSnapshot> result) { if (destroyed) return; rules.clear(); for (BlackListRuleSnapshot rule : result) rules.add(rule.getCanonicalPattern()); showRules(); }
    private void showRules() { adapter.notifyDataSetChanged(); emptyView.showTextView(); }

    private void showAddDialog() {
        if (getContext() == null) return;
        EditText input = new EditText(getContext());
        input.setHint(getString(R.string.CleargramEnterRule)); input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack)); input.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2)); input.setSingleLine(true);
        FrameLayout container = new FrameLayout(getContext());
        container.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider()).setTitle(getString(R.string.CleargramAddRule)).setView(container)
                .setNegativeButton(getString(R.string.Cancel), null).setPositiveButton(getString(R.string.Add), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String pattern = input.getText().toString();
            if (pattern.trim().isEmpty()) { input.setError(getString(R.string.CleargramRuleInvalid)); return; }
            addRule(pattern, dialog, input);
        }));
        showDialog(dialog);
    }

    private void addRule(String pattern, AlertDialog dialog, EditText input) {
        if (operationInProgress) return;
        operationInProgress = true;
        if (mode == MODE_WHITE_LIST) whiteGateway.addRule(pattern, true, new TelegramWhiteListManagementGateway.Callback<WhiteListRuleSnapshot>() {
            @Override public void onSuccess(WhiteListRuleSnapshot result) { completeAdd(dialog); }
            @Override public void onFailure(RuntimeException error) { failAdd(error, input); }
        });
        else blackGateway.addRule(pattern, new TelegramBlackListManagementGateway.Callback<BlackListRuleSnapshot>() {
            @Override public void onSuccess(BlackListRuleSnapshot result) { completeAdd(dialog); }
            @Override public void onFailure(RuntimeException error) { failAdd(error, input); }
        });
    }

    private void completeAdd(AlertDialog dialog) { operationInProgress = false; if (!destroyed) { dialog.dismiss(); loadRules(); } }
    private void failAdd(RuntimeException error, EditText input) { FileLog.e(error); operationInProgress = false; if (!destroyed) { if (error instanceof IllegalArgumentException) input.setError(getString(R.string.CleargramRuleInvalid)); else showOperationError(); } }

    private void removeRule(String pattern) {
        if (operationInProgress) return;
        operationInProgress = true;
        if (mode == MODE_WHITE_LIST) whiteGateway.removeRule(pattern, new RemoveWhiteCallback());
        else blackGateway.removeRule(pattern, new RemoveBlackCallback());
    }
    private void removed() { operationInProgress = false; if (!destroyed) loadRules(); }
    private void showLoadFailure(RuntimeException error) { FileLog.e(error); operationInProgress = false; if (!destroyed && emptyView != null) { emptyView.setText(getString(R.string.CleargramOperationFailed)); emptyView.showTextView(); } }
    private void showMutationFailure(RuntimeException error) { FileLog.e(error); operationInProgress = false; if (!destroyed) showOperationError(); }
    private void showOperationError() { if (getContext() != null) showDialog(new AlertDialog.Builder(getContext(), getResourceProvider()).setMessage(getString(R.string.CleargramOperationFailed)).setPositiveButton(getString(R.string.OK), null).create()); }
    private final class RemoveWhiteCallback implements TelegramWhiteListManagementGateway.Callback<Boolean> { @Override public void onSuccess(Boolean result) { removed(); } @Override public void onFailure(RuntimeException error) { showMutationFailure(error); } }
    private final class RemoveBlackCallback implements TelegramBlackListManagementGateway.Callback<Boolean> { @Override public void onSuccess(Boolean result) { removed(); } @Override public void onFailure(RuntimeException error) { showMutationFailure(error); } }

    private final class RulesAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context; RulesAdapter(Context context) { this.context = context; }
        @Override public int getItemCount() { return rules.size(); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return !operationInProgress; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            TextSettingsCell cell = new TextSettingsCell(context); cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(cell);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            String pattern = rules.get(position); TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            cell.setTextAndIcon(pattern, R.drawable.msg_delete, position != rules.size() - 1);
            cell.setOnClickListener(null);
            cell.getValueImageView().setContentDescription(getString(R.string.CleargramDeleteRule));
            cell.getValueImageView().setOnClickListener(view -> removeRule(pattern));
        }
    }
}
