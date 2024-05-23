// TouristResponse.kt
package com.example.rootmap

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(name = "response", strict = false)
data class TouristResponse(
    @field:Element(name = "header", required = false)
    var header: Header? = null,
    @field:Element(name = "body", required = false)
    var body: Body? = null
)

@Root(name = "header", strict = false)
data class Header(
    @field:Element(name = "resultCode", required = false)
    var resultCode: String? = null,
    @field:Element(name = "resultMsg", required = false)
    var resultMsg: String? = null
)

@Root(name = "body", strict = false)
data class Body(
    @field:Element(name = "items", required = false)
    var items: Items? = null
)

@Root(name = "items", strict = false)
data class Items(
    @field:ElementList(inline = true, required = false)
    var item: List<TouristItem>? = null
)

@Root(name = "item", strict = false)
data class TouristItem(
    @field:Element(name = "addr1", required = false)
    var addr1: String? = null,

    @field:Element(name = "addr2", required = false)
    var addr2: String? = null,

    @field:Element(name = "title", required = false)
    var title: String? = null,

    @field:Element(name = "firstimage", required = false)
    var firstimage: String? = null
)
