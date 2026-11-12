package com.pik.app.data.db

import androidx.room.TypeConverter
import com.pik.app.domain.AccountType
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.DebtType
import com.pik.app.domain.RecurrenceUnit
import com.pik.app.domain.RecurringKind
import com.pik.app.domain.TransactionType
import com.pik.app.domain.importing.ImportDirection
import com.pik.app.domain.importing.ImportSource
import com.pik.app.domain.importing.ImportStatus
import com.pik.app.domain.importing.ImportSuggestion
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

    @TypeConverter
    fun debtEntryTypeToString(value: DebtEntryType?): String? = value?.name

    @TypeConverter
    fun stringToDebtEntryType(value: String?): DebtEntryType? = value?.let { DebtEntryType.valueOf(it) }

    @TypeConverter
    fun debtTypeToString(value: DebtType?): String? = value?.name

    @TypeConverter
    fun stringToDebtType(value: String?): DebtType? = value?.let { DebtType.valueOf(it) }

    @TypeConverter
    fun recurringKindToString(value: RecurringKind?): String? = value?.name

    @TypeConverter
    fun stringToRecurringKind(value: String?): RecurringKind? = value?.let { RecurringKind.valueOf(it) }

    @TypeConverter
    fun recurrenceUnitToString(value: RecurrenceUnit?): String? = value?.name

    @TypeConverter
    fun stringToRecurrenceUnit(value: String?): RecurrenceUnit? = value?.let { RecurrenceUnit.valueOf(it) }

    @TypeConverter
    fun importSourceToString(value: ImportSource?): String? = value?.name

    @TypeConverter
    fun stringToImportSource(value: String?): ImportSource? = value?.let { ImportSource.valueOf(it) }

    @TypeConverter
    fun importDirectionToString(value: ImportDirection?): String? = value?.name

    @TypeConverter
    fun stringToImportDirection(value: String?): ImportDirection? = value?.let { ImportDirection.valueOf(it) }

    @TypeConverter
    fun importStatusToString(value: ImportStatus?): String? = value?.name

    @TypeConverter
    fun stringToImportStatus(value: String?): ImportStatus? = value?.let { ImportStatus.valueOf(it) }

    @TypeConverter
    fun importSuggestionToString(value: ImportSuggestion?): String? = value?.name

    @TypeConverter
    fun stringToImportSuggestion(value: String?): ImportSuggestion? = value?.let { ImportSuggestion.valueOf(it) }
}
