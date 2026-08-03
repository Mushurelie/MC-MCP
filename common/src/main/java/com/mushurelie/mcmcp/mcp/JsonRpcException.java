package com.mushurelie.mcmcp.mcp;

import com.google.gson.JsonElement;

/**
 * Exception portant un code + message JSON-RPC 2.0 (et une donnee optionnelle),
 * utilisee pour transformer proprement toute erreur en reponse JSON-RPC error.
 */
public class JsonRpcException extends RuntimeException {

    /** -32700 Parse error : JSON invalide. */
    public static final int PARSE_ERROR = -32700;
    /** -32600 Invalid Request : structure JSON-RPC invalide. */
    public static final int INVALID_REQUEST = -32600;
    /** -32601 Method not found. */
    public static final int METHOD_NOT_FOUND = -32601;
    /** -32602 Invalid params. */
    public static final int INVALID_PARAMS = -32602;
    /** -32603 Internal error. */
    public static final int INTERNAL_ERROR = -32603;
    /** -32000 Erreur applicative generique (outil interdit, mode lecture seule, etc.). */
    public static final int SERVER_ERROR = -32000;
    /** -32001 Outil non trouve. */
    public static final int TOOL_NOT_FOUND = -32001;
    /** -32002 Outil refuse (readOnlyMode / blacklist / whitelist / confirmation refusee). */
    public static final int TOOL_FORBIDDEN = -32002;

    private final int code;
    private final transient JsonElement data;

    public JsonRpcException(int code, String message) {
        this(code, message, null);
    }

    public JsonRpcException(int code, String message, JsonElement data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public JsonElement getData() {
        return data;
    }
}
