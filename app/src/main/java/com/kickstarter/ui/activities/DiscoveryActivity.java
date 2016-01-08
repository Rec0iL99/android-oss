package com.kickstarter.ui.activities;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.kickstarter.KSApplication;
import com.kickstarter.R;
import com.kickstarter.libs.ActivityRequestCodes;
import com.kickstarter.libs.ApiCapabilities;
import com.kickstarter.libs.BaseActivity;
import com.kickstarter.libs.RecyclerViewPaginator;
import com.kickstarter.libs.RefTag;
import com.kickstarter.libs.qualifiers.RequiresViewModel;
import com.kickstarter.libs.utils.DiscoveryUtils;
import com.kickstarter.libs.utils.StatusBarUtils;
import com.kickstarter.models.Project;
import com.kickstarter.services.ApiClientType;
import com.kickstarter.services.DiscoveryParams;
import com.kickstarter.services.apiresponses.InternalBuildEnvelope;
import com.kickstarter.ui.IntentKey;
import com.kickstarter.ui.adapters.DiscoveryAdapter;
import com.kickstarter.ui.containers.ApplicationContainer;
import com.kickstarter.ui.intents.DiscoveryIntentAction;
import com.kickstarter.ui.toolbars.DiscoveryToolbar;
import com.kickstarter.ui.viewholders.DiscoveryOnboardingViewHolder;
import com.kickstarter.ui.viewholders.ProjectCardViewHolder;
import com.kickstarter.viewmodels.DiscoveryViewModel;

import java.net.CookieManager;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.BindDrawable;
import butterknife.ButterKnife;
import rx.android.schedulers.AndroidSchedulers;

@RequiresViewModel(DiscoveryViewModel.class)
public final class DiscoveryActivity extends BaseActivity<DiscoveryViewModel> implements DiscoveryAdapter.Delegate {
  private DiscoveryAdapter adapter;
  private DiscoveryIntentAction intentAction;
  private LinearLayoutManager layoutManager;
  private RecyclerViewPaginator recyclerViewPaginator;

  protected @Inject ApplicationContainer applicationContainer;
  protected @Inject ApiClientType client;
  protected @Inject CookieManager cookieManager;

  @BindDrawable(R.drawable.dark_blue_gradient) Drawable darkBlueGradientDrawable;
  @Bind(R.id.discovery_layout) LinearLayout discoveryLayout;
  @Bind(R.id.discovery_toolbar) DiscoveryToolbar discoveryToolbar;
  public @Bind(R.id.recycler_view) RecyclerView recyclerView;

  @Override
  protected void onCreate(final @Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ((KSApplication) getApplication()).component().inject(this);
    final ViewGroup container = applicationContainer.bind(this);
    final LayoutInflater layoutInflater = getLayoutInflater();

    layoutInflater.inflate(R.layout.discovery_layout, container);
    ButterKnife.bind(this, container);

    layoutManager = new LinearLayoutManager(this);
    adapter = new DiscoveryAdapter(this);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(adapter);

    intentAction = new DiscoveryIntentAction(viewModel.inputs::initializer, lifecycle(), client);
    intentAction.intent(getIntent());

    recyclerViewPaginator = new RecyclerViewPaginator(recyclerView, viewModel.inputs::nextPage);

    viewModel.outputs.shouldShowOnboarding()
      .compose(bindToLifecycle())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(adapter::setShouldShowOnboardingView);

    viewModel.outputs.projects()
      .compose(bindToLifecycle())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(adapter::takeProjects);

    viewModel.outputs.params()
      .compose(bindToLifecycle())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(this::loadParams);

    viewModel.outputs.showFilters()
      .compose(bindToLifecycle())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(this::startDiscoveryFilterActivity);

    viewModel.outputs.showProject()
      .compose(bindToLifecycle())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(projectAndRefTag -> this.startProjectActivity(projectAndRefTag.first, projectAndRefTag.second));
  }

  @Override
  protected void onNewIntent(final @NonNull Intent intent) {
    intentAction.intent(intent);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    recyclerViewPaginator.stop();
  }

  public void projectCardClick(final @NonNull ProjectCardViewHolder viewHolder, final @NonNull Project project) {
    viewModel.inputs.projectClicked(project);
  }

  public void signupLoginClick(final @NonNull DiscoveryOnboardingViewHolder viewHolder) {
    final Intent intent = new Intent(this, LoginToutActivity.class)
      .putExtra(IntentKey.LOGIN_TYPE, LoginToutActivity.REASON_GENERIC);
    startActivity(intent);
  }

  private void loadParams(final @NonNull DiscoveryParams params) {
    discoveryToolbar.loadParams(params);

    if (ApiCapabilities.canSetStatusBarColor() && ApiCapabilities.canSetDarkStatusBarIcons()) {
      StatusBarUtils.apply(this, DiscoveryUtils.primaryColor(this, params), DiscoveryUtils.overlayShouldBeLight(params));
    }
  }

  private void startDiscoveryFilterActivity(final @NonNull DiscoveryParams params) {
    final Intent intent = new Intent(this, DiscoveryFilterActivity.class)
      .putExtra(IntentKey.DISCOVERY_PARAMS, params);

    startActivityForResult(intent, ActivityRequestCodes.DISCOVERY_ACTIVITY_DISCOVERY_FILTER_ACTIVITY_SELECT_FILTER);
  }

  private void startProjectActivity(final @NonNull Project project, final @NonNull RefTag refTag) {
    final Intent intent = new Intent(this, ProjectActivity.class)
      .putExtra(IntentKey.PROJECT, project)
      .putExtra(IntentKey.REF_TAG, refTag);
    startActivity(intent);
    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slide_out_left);
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final @NonNull Intent intent) {
    if (requestCode != ActivityRequestCodes.DISCOVERY_ACTIVITY_DISCOVERY_FILTER_ACTIVITY_SELECT_FILTER) {
      return;
    }

    if (resultCode != RESULT_OK) {
      return;
    }

    intentAction.intent(intent);
  }

  public void showBuildAlert(final @NonNull InternalBuildEnvelope envelope) {
    new AlertDialog.Builder(this)
      .setTitle(getString(R.string.Upgrade_app))
      .setMessage(getString(R.string.A_newer_build_is_available))
      .setPositiveButton(android.R.string.yes, (dialog, which) -> {
        Intent intent = new Intent(this, DownloadBetaActivity.class)
          .putExtra(IntentKey.INTERNAL_BUILD_ENVELOPE, envelope);
        startActivity(intent);
      })
      .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
      })
      .setIcon(android.R.drawable.ic_dialog_alert)
      .show();
  }
}
