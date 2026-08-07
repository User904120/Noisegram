package org.telegram.ui.Cells;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.Theme;

/** Test-only attached host for ordinary ChatMessageCell baseline coverage. */
public final class CleargramChatMessageCellHostActivity extends Activity {

    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.createChatResources(this, false);
        root = new FrameLayout(this);
        setContentView(root);
    }

    FrameLayout getRoot() {
        return root;
    }

    @Override
    protected void onDestroy() {
        if (root != null) {
            root.removeAllViews();
        }
        super.onDestroy();
    }
}
