package jr.brian.commandpannel.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-wide Hilt bindings. [jr.brian.commandpannel.privileged.PrivilegedAccess] is a constructor-injected
 * `@Singleton`, so no explicit provides is needed yet; this module is the home for future bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
