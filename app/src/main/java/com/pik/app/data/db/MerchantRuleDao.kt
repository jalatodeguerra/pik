package com.pik.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MerchantRuleDao {

    @Query("SELECT * FROM merchant_rules")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Insert
    suspend fun insertAll(rules: List<MerchantRuleEntity>)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()
}
