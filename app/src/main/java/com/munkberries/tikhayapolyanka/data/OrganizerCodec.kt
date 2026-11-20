package com.munkberries.tikhayapolyanka.data

import com.munkberries.tikhayapolyanka.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

object OrganizerCodec {
    fun marks(ranges: List<BoldRange>): String = JSONArray().apply {
        ranges.forEach { put(JSONArray().put(it.start).put(it.end)) }
    }.toString()
    fun marks(raw: String): List<BoldRange> = JSONArray(raw).let { array ->
        (0 until array.length()).map { val a=array.getJSONArray(it);BoldRange(a.getInt(0),a.getInt(1)) }
    }
    fun colors(ranges: List<ColorRange>): String = JSONArray().apply {
        ranges.forEach { put(JSONObject().put("start",it.start).put("end",it.end).put("color",it.color.name)) }
    }.toString()
    fun colors(raw: String): List<ColorRange> = JSONArray(raw).let { a ->
        (0 until a.length()).map { val j=a.getJSONObject(it)
            ColorRange(j.getInt("start"),j.getInt("end"),TextColor.valueOf(j.getString("color"))) }
    }
    fun task(t: PlannedTask): String = JSONObject().apply {
        put("schema",1);put("id",t.id);put("kind",t.kind.name);put("title",t.title)
        put("date",t.date.toString());put("time",t.time?.toString());put("description",t.description)
        put("completed",t.completed);put("reminder",t.reminder?.toString())
        put("revision",t.revision);put("remindedRevision",t.remindedRevision);put("deleted",t.deleted)
    }.toString()
    fun task(raw: String): PlannedTask = JSONObject(raw).let { j ->
        check(j.optInt("schema",1)==1)
        PlannedTask(j.getString("id"),TaskKind.valueOf(j.getString("kind")),j.getString("title"),
            LocalDate.parse(j.getString("date")),j.nullable("time")?.let(LocalTime::parse),
            j.optString("description"),j.optBoolean("completed"),
            j.nullable("reminder")?.let(LocalDateTime::parse),j.getLong("revision"),
            j.optLong("remindedRevision",-1),j.optBoolean("deleted"))
    }
}
fun JSONObject.nullable(key: String): String? = if(!has(key) || isNull(key)) null else getString(key)
