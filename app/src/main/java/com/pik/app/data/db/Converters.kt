package com.pik.app.data.db

import androidx.room.TypeConverter
import com.pik.app.domain.AccountType
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.TransactionType
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun accountTypeToString(value: AccountType?): String? = value?.name

    @TypeConverter
    fun stringToAccountType(value: String?): AccountType? = value?.let { AccountType.valueOf(it) }

    @TypeConverter
    fun categoryKindToString(value: CategoryKind?): String? = value?.name

    @TypeConverter
    fun stringToCategoryKind(value: String?): CategoryKind? = value?.let { CategoryKind.valueOf(it) }

    @TypeConverter
    fun transactionTypeToString(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun stringToTransactionType(value: String?): TransactionType? = value?.let { TransactionType.valueOf(it) }
}
