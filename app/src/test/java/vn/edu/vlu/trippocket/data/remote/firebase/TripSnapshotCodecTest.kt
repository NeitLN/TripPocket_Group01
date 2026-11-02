package vn.edu.vlu.trippocket.data.remote.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A3/A4: the backup document written and read back as Firestore stores it. */
class TripSnapshotCodecTest {

    companion object {
        /** A trip with one row of every kind, shared by the restore tests. */
        fun fullSnapshot(tripId: String = "t1", ownerUid: String = "uid-a", revision: Long = 7) = TripSnapshot(
            tripId = tripId,
            ownerUid = ownerUid,
            name = "Đà Lạt mùa hoa",
            destination = "Lâm Đồng",
            startDate = 20_400,
            endDate = 20_402,
            localRevision = revision,
            updatedAt = 1_700_000_000_000,
            members = listOf(MemberSnapshot("$tripId-an", "An", 0), MemberSnapshot("$tripId-binh", "Bình", 1)),
            // In the order Room lists them (day, then order), so a restored trip maps back equal.
            activities = listOf(
                ActivitySnapshot("$tripId-a2", 0, "Hồ Xuân Hương", "", 8 * 60, 0, "", 0, null),
                ActivitySnapshot("$tripId-a1", 1, "Chợ đêm", "Chợ Đà Lạt", 19 * 60, 150_000, "Mang áo ấm", 0, 30),
            ),
            checklist = listOf(ChecklistItemSnapshot("$tripId-c1", "Áo khoác", isDone = true)),
            places = listOf(PlaceSnapshot("$tripId-p1", "Đà Lạt", "Việt Nam", 11.94, 108.44, "Ghi chú")),
            expenses = listOf(
                ExpenseSnapshot(
                    "$tripId-e1", "Lẩu gà lá é", 300_001, "$tripId-an", "FOOD", "EQUAL", 20_400, 1_700_000_000_500,
                    mapOf("$tripId-an" to 150_001L, "$tripId-binh" to 150_000L),
                ),
            ),
        )
    }

    @Test
    fun aSnapshot_readsBackUnchanged() {
        val snapshot = fullSnapshot()
        assertEquals(snapshot, TripSnapshotCodec.fromMap(TripSnapshotCodec.toMap(snapshot)))
    }

    @Test
    fun theMap_hasTheFieldsFirestoreSizeIsMeasuredOn() {
        // FirestoreDocumentSize measures the snapshot by its bean names; the stored map must be
        // the same document, or the 1 MiB check would measure something else.
        val snapshot = fullSnapshot()
        val path = listOf("users", snapshot.ownerUid, "trips", snapshot.tripId)
        val ofMap = path.sumOf(FirestoreDocumentSize::ofString) + 16 +
            FirestoreDocumentSize.ofValue(TripSnapshotCodec.toMap(snapshot)) + 32

        assertEquals(FirestoreDocumentSize.ofTripBackup(snapshot), ofMap)
    }

    @Test
    fun wholeNumbersComeBackAsLong_andDecimalsAsDouble_likeFirestore() {
        val map = TripSnapshotCodec.toMap(fullSnapshot()).toMutableMap()
        // Firestore hands back every integer as Long; a whole-number coordinate is still a Double.
        map["members"] = listOf(mapOf("memberId" to "m", "name" to "An", "orderIndex" to 0L))
        map["places"] = listOf(
            mapOf("placeId" to "p", "name" to "Huế", "country" to "", "latitude" to 16.0, "longitude" to 107L, "note" to "")
        )

        val read = TripSnapshotCodec.fromMap(map)

        assertEquals(0, read.members.single().orderIndex)
        assertEquals(107.0, read.places.single().longitude, 0.0)
    }

    @Test
    fun aDocumentUploadedBeforeTheCodec_withNoReminderField_reads() {
        val map = TripSnapshotCodec.toMap(fullSnapshot()).toMutableMap()
        map["activities"] = listOf(
            mapOf(
                "activityId" to "a", "dayIndex" to 0L, "title" to "x", "place" to "", "startTime" to 60L,
                "estimatedCost" to 0L, "note" to "", "orderIndex" to 0L,
            )
        )
        assertNull(TripSnapshotCodec.fromMap(map).activities.single().reminderMinutesBefore)
    }

    @Test(expected = InvalidSnapshotException::class)
    fun aMissingField_isRejected() {
        TripSnapshotCodec.fromMap(TripSnapshotCodec.toMap(fullSnapshot()) - "endDate")
    }

    @Test(expected = InvalidSnapshotException::class)
    fun aValueOfTheWrongType_isRejected() {
        TripSnapshotCodec.fromMap(TripSnapshotCodec.toMap(fullSnapshot()) + ("startDate" to "2026-11-01"))
    }

    @Test(expected = InvalidSnapshotException::class)
    fun aShareThatIsNotAWholeNumber_isRejected() {
        val map = TripSnapshotCodec.toMap(fullSnapshot()).toMutableMap()
        map["expenses"] = (map["expenses"] as List<*>).map { (it as Map<*, *>) + ("shares" to mapOf("t1-an" to 1.5)) }
        TripSnapshotCodec.fromMap(map)
    }
}
