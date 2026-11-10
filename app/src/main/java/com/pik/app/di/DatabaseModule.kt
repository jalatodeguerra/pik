package com.pik.app.di

import android.content.Context
import androidx.room.Room
import com.pik.app.data.db.AccountDao
import com.pik.app.data.db.CategoryDao
import com.pik.app.data.db.PikDatabase
import com.pik.app.data.db.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PikDatabase =
        Room.databaseBuilder(context, PikDatabase::class.java, PikDatabase.NAME).build()

    @Provides
    fun provideAccountDao(db: PikDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCategoryDao(db: PikDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: PikDatabase): TransactionDao = db.transactionDao()
}
