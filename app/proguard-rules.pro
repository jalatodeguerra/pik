# Pik release rules (R8 is on for release builds).
#
# Nothing special is needed for the libraries Pik uses:
#  - Room (entities, DAOs, the generated database) and its KSP-generated code are kept by Room's own
#    consumer rules.
#  - Hilt / Dagger and WorkManager (HiltWorkerFactory) ship consumer rules.
#  - Compose, DataStore, Navigation and Biometric ship consumer rules.
#  - Backups are read and written with org.json, not with reflection, so the backup data classes may
#    be shrunk and renamed freely.
#
# Keep line numbers in crash reports readable, with the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
