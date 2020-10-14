/*
 * Copyright (C) 2020 Codepunk, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* TODO Maybe get ride of currentToast setter and makeAndShowToast methods. Can simplify this. */

package com.codepunk.core.ui.settings

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.contains
import com.codepunk.core.BuildConfig.PREF_KEY_ABOUT
import com.codepunk.core.BuildConfig.PREF_KEY_DEV_OPTS_ENABLED
import com.codepunk.core.BuildConfig.VERSION_NAME
import com.codepunk.core.BuildConfig.EXTRA_DEV_OPTS_PASSWORD_HASH
import com.codepunk.core.R
import com.codepunk.core.di.factory.ViewModelFactory
import com.codepunk.doofenschmirtz.android.settings.widget.MasterSwitchPreference
import com.codepunk.doofenschmirtz.delegate.consume
import com.codepunk.doofenschmirtz.dialog.DialogFragmentAlertinator
import com.codepunk.doofenschmirtz.dialog.DialogFragmentAlertinator.OnDialogResultListener
import com.codepunk.doofenschmirtz.settings.requirePreference
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.AndroidSupportInjection
import dagger.android.support.HasSupportFragmentInjector
import javax.inject.Inject
import javax.inject.Provider

// region Constants

/**
 * The number of clicks remaining at which to show a [Toast] text.
 */
private const val DEV_OPTS_CLICKS_REMAINING_TOAST = 3

/**
 * The request code used by the developer password dialog fragment.
 */
private const val DEV_PASSWORD_REQUEST_CODE = 0

// endregion Constants

/**
 * A preference fragment that displays the main settings available to the user.
 */
class MainSettingsFragment :
    HasSupportFragmentInjector,
    PreferenceFragmentCompat(),
    OnPreferenceClickListener,
    OnDialogResultListener {

    // region Companion object

    companion object {

        // region Properties

        /**
         * The fragment tag to use for the developer password dialog fragment.
         */
        @JvmStatic
        private val DEV_PASSWORD_DIALOG_FRAGMENT_TAG =
            "${MainSettingsFragment::class.java.name}.DEVELOPER_PASSWORD_DIALOG"

        // endregion Properties

    }

    // endregion Companion object

    // region Properties

    /**
     * Performs dependency injection on fragments.
     */
    @Inject
    lateinit var fragmentDispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>

    /**
     * The injected [ViewModelProvider.Factory] that we will use to get an instance of
     * [SettingsViewModel].
     */
    @Inject
    lateinit var viewModelFactory: ViewModelFactory<SettingsViewModel>

    /**
     * The injected [Provider] that we will use to get an instance of [DialogFragmentAlertinator].
     */
    @Inject
    lateinit var mDialogFragmentAlertinatorProvider: Provider<DialogFragmentAlertinator>

    /**
     * The injected [Provider] that we will use to get an instance of
     * [DevOptsPasswordDialogFragment].
     */
    @Inject
    lateinit var devOptsPasswordDialogFragmentProvider: Provider<DevOptsPasswordDialogFragment>

    /**
     * The [SettingsViewModel] instance backing this fragment.
     */
    private val viewModel: SettingsViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(SettingsViewModel::class.java)
    }

    /**
     * The [NavController] used by this fragment.
     */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.nav_fragment)
    }

    /**
     * The about preference.
     */
    private val aboutPreference by lazy {
        requirePreference<Preference>(PREF_KEY_ABOUT)
    }

    /**
     * The developer options preference.
     */
    private val devOptsEnabledPreference by lazy {
        requirePreference<MasterSwitchPreference>(PREF_KEY_DEV_OPTS_ENABLED)
    }

    /**
     * The currently-displayed toast.
     */
    private var currentToast: Toast? = null
        set(value) {
            field?.cancel()
            field = value
        }

    // endregion Properties

    // region Lifecycle methods

    /**
     * Injects dependencies into this fragment.
     */
    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    /**
     * Updates the activity title.
     */
    override fun onStart() {
        super.onStart()
        requireActivity().title = preferenceScreen.title
    }

    // endregion Lifecycle methods

    // region Inherited methods

    /**
     * Creates and initializes preferences.
     */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_settings, rootKey)
        aboutPreference.summary = getString(R.string.settings_about_summary, VERSION_NAME)
        aboutPreference.onPreferenceClickListener = this

        devOptsEnabledPreference.onPreferenceClickListener = this

        viewModel.requestsRemaining.observe(this) { onRequestsRemaining(it) }
        viewModel.alreadyDeveloper.observe(this) { onAlreadyDeveloper(it) }
        viewModel.devOptsUnlocked.observe(this) { onDevOptsUnlocked(it) }
        viewModel.devOptsEnabled.observe(this) { onDevOptsEnabled(it) }

        // Set initial state for preference widgets
        viewModel.devOptsUnlocked.value?.also { onDevOptsUnlocked(it) }
    }

    // endregion Inherited methods

    // region Implemented methods

    /**
     * Implementation of [HasSupportFragmentInjector]. Returns a
     * [DispatchingAndroidInjector] that injects dependencies into fragments.
     */
    override fun supportFragmentInjector(): AndroidInjector<Fragment> =
        fragmentDispatchingAndroidInjector

    /**
     * Implementation of [OnPreferenceClickListener]. Reacts to a preference being clicked.
     */
    override fun onPreferenceClick(preference: Preference?): Boolean =
        when (preference) {
            aboutPreference -> viewModel.requestDevOpts()
            devOptsEnabledPreference -> {
                if (devOptsEnabledPreference.isChecked) {
                    navController.navigate(R.id.action_main_to_dev_opts)
                }
                true
            }
            else -> false
        }

    /**
     * Implementation of [OnDialogResultListener]. Responds to the result of the dialog based
     * on [requestCode], depending on [resultCode] and [data].
     */
    override fun onDialogResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                DEV_PASSWORD_REQUEST_CODE -> viewModel.unlockDevOpts(
                    data?.getStringExtra(EXTRA_DEV_OPTS_PASSWORD_HASH)
                )
            }
        }
    }

    // endregion Implemented methods

    // region Methods

    /**
     * Responds to the user requesting developer options.
     */
    private fun onRequestsRemaining(requestRemaining: Lazy<Int>) {
        requestRemaining.consume { value ->
            when (value) {
                0 -> showDevOptsPasswordDialogFragment()
                in 1..DEV_OPTS_CLICKS_REMAINING_TOAST ->
                    makeAndShowToast(
                        resources.getQuantityString(
                            R.plurals.settings_clicks_remaining,
                            value,
                            value
                        )
                    )
            }
        }
    }

    /**
     * Responds to a developer options request being made when the user is already a developer.
     */
    private fun onAlreadyDeveloper(isAlreadyDeveloper: Lazy<Boolean>) {
        isAlreadyDeveloper.consume { makeAndShowToast(R.string.settings_already_a_developer) }
    }

    /**
     * Responds to developer options becoming unlocked.
     */
    private fun onDevOptsUnlocked(unlocked: Lazy<Boolean>) {
        val value = unlocked.consume {
            makeAndShowToast(R.string.settings_now_a_developer)
        }
        updatePreferenceScreen(value)
    }

    /**
     * Responds to developer options being enabled or disabled.
     */
    private fun onDevOptsEnabled(enabled: Boolean) {
        devOptsEnabledPreference.isChecked = enabled
        devOptsEnabledPreference.setSummary(if (enabled) R.string.enabled else R.string.disabled)
    }

    /**
     * Convenience method to make and show a [Toast] using the given [text].
     */
    private fun makeAndShowToast(text: CharSequence): Toast =
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).apply {
            currentToast = this
            show()
        }

    /**
     * Convenience method to make and show a [Toast] using the given [resId].
     */
    private fun makeAndShowToast(@StringRes resId: Int): Toast =
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).apply {
            currentToast = this
            show()
        }

    /**
     * Convenience method to show a developer options password dialog fragment.
     */
    private fun showDevOptsPasswordDialogFragment() {
        if (parentFragmentManager.findFragmentByTag(DEV_PASSWORD_DIALOG_FRAGMENT_TAG) != null) {
            return
        }
        currentToast = null
        devOptsPasswordDialogFragmentProvider.get().also {
            it.setTargetFragment(this, DEV_PASSWORD_REQUEST_CODE)
        }.showForResult(
            this,
            DEV_PASSWORD_REQUEST_CODE,
            DEV_PASSWORD_DIALOG_FRAGMENT_TAG
        )
    }

    /**
     * Hides or shows preferences as appropriate.
     */
    private fun updatePreferenceScreen(devOptsUnlocked: Boolean) {
        when {
            devOptsUnlocked && !preferenceScreen.contains(devOptsEnabledPreference) ->
                preferenceScreen.addPreference(devOptsEnabledPreference)
            !devOptsUnlocked && preferenceScreen.contains(devOptsEnabledPreference) ->
                preferenceScreen.removePreference(devOptsEnabledPreference)
        }
    }

    // endregion Methods

}
