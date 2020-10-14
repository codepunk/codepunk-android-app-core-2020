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

package com.codepunk.core.di.module

import android.content.Context
import com.codepunk.core.di.qualifier.ActivityContext
import com.codepunk.core.di.scope.ActivityScope
import com.codepunk.core.di.scope.FragmentScope
import com.codepunk.core.ui.settings.DevOptsPasswordDialogFragment
import com.codepunk.core.ui.settings.MainSettingsFragment
import com.codepunk.core.ui.settings.SettingsActivity
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

/**
 * A [Module] for injecting dependencies into [SettingsActivity].
 */
@Module
abstract class SettingsActivityModule {

    // region Companion object

    @Module
    companion object {

        /* Here’s an example of how we can provide something at this level
        @JvmStatic
        @Provides
        @ActivityScope
        fun providesSomething(): String = "Hello"
         */

    }

    // endregion Companion object

    // region Methods

    /**
     * Binds the activity-level [Context] for dependency injection.
     */
    @Suppress("UNUSED")
    @Binds
    @ActivityScope
    @ActivityContext
    abstract fun bindsActivityContext(activity: SettingsActivity): Context

    /**
     * Contributes an AndroidInjector to [MainSettingsFragment].
     */
    @Suppress("UNUSED")
    @FragmentScope
    @ContributesAndroidInjector(modules = [MainSettingsFragmentModule::class])
    abstract fun contributeMainSettingsFragmentInjector(): MainSettingsFragment

    /**
     * Contributes an AndroidInjector to [DevOptsPasswordDialogFragment].
     */
    @Suppress("UNUSED")
    @FragmentScope
    @ContributesAndroidInjector(modules = [DevOptsPasswordDialogFragmentModule::class])
    abstract fun contributeDevOptsPasswordDialogFragmentInjector(): DevOptsPasswordDialogFragment

    // endregion Methods

}
