package com.omnifile.preview

internal object PdfRendererProtocol {
    const val OPEN = 1
    const val RENDER = 2
    const val CLOSE = 3
    const val CANCEL = 4
    const val KILL_WORKER_FOR_TEST = 5
    const val OPENED = 11
    const val PAGE = 12
    const val CLOSED = 13
    const val ERROR = 14

    const val REQUEST_ID = "request_id"
    const val SESSION_ID = "session_id"
    const val PAGE_INDEX = "page_index"
    const val PAGE_COUNT = "page_count"
    const val WIDTH = "width"
    const val HEIGHT = "height"
    const val ERROR_KIND = "error_kind"

    const val ERROR_MALFORMED = 1
    const val ERROR_ENCRYPTED = 2
    const val ERROR_RESOURCE = 3
    const val ERROR_INVALID_PAGE = 4
    const val ERROR_UNAVAILABLE = 5
}
