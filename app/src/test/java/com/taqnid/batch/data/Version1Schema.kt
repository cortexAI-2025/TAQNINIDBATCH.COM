package com.taqnid.batch.data

/**
 * Schéma de la base version 1, tel que Room 2.6 le créait à partir des
 * entités d'origine (avant l'ajout de la version de lot et de l'état de
 * synchronisation). Sert à vérifier la migration 1 → 2 sur une vraie base.
 */
object Version1Schema {
    val STATEMENTS = listOf(
        "CREATE TABLE IF NOT EXISTS `batches` (`id` TEXT NOT NULL, `taqninId` TEXT NOT NULL, `variety` TEXT NOT NULL, " +
            "`type` TEXT NOT NULL, `initialQuantity` REAL NOT NULL, `currentQuantity` REAL NOT NULL, `unit` TEXT NOT NULL, " +
            "`locationId` TEXT NOT NULL, `locationName` TEXT NOT NULL, `currentStage` TEXT NOT NULL, " +
            "`complianceStatus` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `ownerName` TEXT NOT NULL, " +
            "`licenseNumber` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, " +
            "`expirationDate` INTEGER, `notes` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_batches_taqninId` ON `batches` (`taqninId`)",
        "CREATE INDEX IF NOT EXISTS `index_batches_currentStage` ON `batches` (`currentStage`)",
        "CREATE INDEX IF NOT EXISTS `index_batches_ownerId` ON `batches` (`ownerId`)",
        "CREATE INDEX IF NOT EXISTS `index_batches_lastUpdated` ON `batches` (`lastUpdated`)",
        "CREATE TABLE IF NOT EXISTS `batch_actions` (`id` TEXT NOT NULL, `batchId` TEXT NOT NULL, `taqninId` TEXT NOT NULL, " +
            "`actionType` TEXT NOT NULL, `performedBy` TEXT NOT NULL, `performedByName` TEXT NOT NULL, " +
            "`timestamp` INTEGER NOT NULL, `quantityBefore` REAL, `quantityAfter` REAL, `quantityDelta` REAL, " +
            "`unit` TEXT NOT NULL, `stageFrom` TEXT, `stageTo` TEXT, `description` TEXT NOT NULL, " +
            "`locationId` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`batchId`) REFERENCES `batches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_batch_actions_batchId` ON `batch_actions` (`batchId`)",
        "CREATE INDEX IF NOT EXISTS `index_batch_actions_timestamp` ON `batch_actions` (`timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_batch_actions_actionType` ON `batch_actions` (`actionType`)",
        "CREATE TABLE IF NOT EXISTS `transfers` (`id` TEXT NOT NULL, `transferNumber` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`originLicenseId` TEXT NOT NULL, `originLicenseName` TEXT NOT NULL, `destinationLicenseId` TEXT NOT NULL, " +
            "`destinationLicenseName` TEXT NOT NULL, `batchIdsJson` TEXT NOT NULL, `batchSummariesJson` TEXT NOT NULL, " +
            "`driverName` TEXT NOT NULL, `vehiclePlate` TEXT NOT NULL, `qrCodeContent` TEXT NOT NULL, " +
            "`createdBy` TEXT NOT NULL, `createdByName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`dispatchedAt` INTEGER, `receivedAt` INTEGER, `receivedBy` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
            "`isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_transfers_transferNumber` ON `transfers` (`transferNumber`)",
        "CREATE INDEX IF NOT EXISTS `index_transfers_status` ON `transfers` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_transfers_createdAt` ON `transfers` (`createdAt`)",
        "CREATE TABLE IF NOT EXISTS `locations` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
            "`licenseId` TEXT NOT NULL, `licenseName` TEXT NOT NULL, `capacity` REAL, `currentLoad` REAL NOT NULL, " +
            "`isActive` INTEGER NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_locations_licenseId` ON `locations` (`licenseId`)",
    )
}
