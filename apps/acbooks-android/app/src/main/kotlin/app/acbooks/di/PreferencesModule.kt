package app.acbooks.di

import android.content.Context
import app.acbooks.core.datastore.AppearancePreferences
import app.acbooks.core.datastore.ProfilePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides
    @Singleton
    fun provideAppearancePreferences(@ApplicationContext context: Context): AppearancePreferences =
        AppearancePreferences(context)

    @Provides
    @Singleton
    fun provideProfilePreferences(@ApplicationContext context: Context): ProfilePreferences =
        ProfilePreferences(context)
}
