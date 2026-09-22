package dk.kvitteringsarkiv.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.math.BigDecimal
import java.time.LocalDate

class ReceiptDatabase(context: Context) : SQLiteOpenHelper(context, "receipts.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE receipts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                supplier TEXT NOT NULL,
                purchase_date TEXT NOT NULL,
                total_cents INTEGER,
                storage_provider TEXT NOT NULL,
                storage_path TEXT NOT NULL,
                ocr_text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_receipts_supplier ON receipts(supplier)")
        db.execSQL("CREATE INDEX idx_receipts_date ON receipts(purchase_date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(draft: ReceiptDraft, provider: String, path: String): Long {
        val values = ContentValues().apply {
            put("supplier", draft.supplier)
            put("purchase_date", draft.purchaseDate.toString())
            draft.total?.let { put("total_cents", it.movePointRight(2).longValueExact()) }
            put("storage_provider", provider)
            put("storage_path", path)
            put("ocr_text", draft.ocrText)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow("receipts", null, values)
    }

    fun search(query: String = "", limit: Int = 50): List<ReceiptRecord> {
        val q = query.trim()
        val (selection, args) = if (q.isBlank()) {
            null to emptyArray<String>()
        } else {
            val like = "%$q%"
            "supplier LIKE ? OR ocr_text LIKE ? OR purchase_date LIKE ?" to arrayOf(like, like, like)
        }

        val cursor = readableDatabase.query(
            "receipts",
            arrayOf("id", "supplier", "purchase_date", "total_cents", "storage_provider", "storage_path", "ocr_text", "created_at"),
            selection,
            args,
            null,
            null,
            "purchase_date DESC, created_at DESC",
            limit.toString(),
        )

        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        ReceiptRecord(
                            id = it.getLong(0),
                            supplier = it.getString(1),
                            purchaseDate = LocalDate.parse(it.getString(2)),
                            total = if (it.isNull(3)) null else BigDecimal.valueOf(it.getLong(3), 2),
                            storageProvider = it.getString(4),
                            storagePath = it.getString(5),
                            ocrText = it.getString(6),
                            createdAt = it.getLong(7),
                        )
                    )
                }
            }
        }
    }
}
