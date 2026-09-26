package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.TvDeviceProfile;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/** Fork-specific identity and update destination; do not install upstream APKs. */
public class AboutSettingsPresenter extends BasePresenter<Void> {
    public AboutSettingsPresenter(Context context) { super(context); }
    public static AboutSettingsPresenter instance(Context context) { return new AboutSettingsPresenter(context); }

    public void show() {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        boolean lite = TvDeviceProfile.get(getContext()).lite;
        dialog.appendSingleButton(UiOptionItem.from(lite ? "Playback profile: Lite" : "Playback profile: Standard",
            option -> MessageHelpers.showMessage(getContext(), lite
                ? "Optimized for 2 GB TVs. 1080p default, bounded buffers, still thumbnails."
                : "Higher quality defaults where the TV supports them. Bounded image and video buffers.")));
        dialog.appendSingleButton(UiOptionItem.from("Tihulu Tube source and builds",
            option -> Utils.openLink(getContext(), Utils.toQrCodeLink("https://github.com/Tihulu/tihulu-brave-tv/tree/codex/tihulu-tube/youtube-tv"))));
        dialog.appendSingleButton(UiOptionItem.from("Built on SmartTube 32.56 · open-source credits",
            option -> Utils.openLink(getContext(), Utils.toQrCodeLink("https://github.com/yuliskov/SmartTube"))));
        dialog.appendSingleButton(UiOptionItem.from("Playback and ad blocking",
            option -> MessageHelpers.showMessage(getContext(), "Native content playback. SponsorBlock is configured separately in Settings. YouTube changes may require an app update.")));
        dialog.showDialog("Tihulu Tube " + AppInfoHelpers.getAppVersionName(getContext()));
    }
}
