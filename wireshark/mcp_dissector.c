/*
 * MCP (Model Context Protocol) Wireshark Dissector
 * Analyzes JSON-RPC 2.0 over WebSocket communication for Chess MCP Server
 * Author: AI Assistant
 * Version: 1.0.0
 */

#include "config.h"
#include <epan/packet.h>
#include <epan/prefs.h>
#include <epan/proto_data.h>
#include <epan/to_str.h>
#include <epan/wmem/wmem.h>
#include <wsutil/str_util.h>

/* Protocol and field registration */
static int proto_mcp = -1;
static int proto_mcp_ws = -1;

/* Field definitions */
static int hf_mcp_version = -1;
static int hf_mcp_method = -1;
static int hf_mcp_id = -1;
static int hf_mcp_params = -1;
static int hf_mcp_result = -1;
static int hf_mcp_error_code = -1;
static int hf_mcp_error_message = -1;
static int hf_mcp_agent_id = -1;
static int hf_mcp_encrypted = -1;
static int hf_mcp_ciphertext = -1;
static int hf_mcp_iv = -1;
static int hf_mcp_ratchet_header = -1;

/* WebSocket fields */
static int hf_ws_opcode = -1;
static int hf_ws_fin = -1;
static int hf_ws_payload_length = -1;
static int hf_ws_payload = -1;

/* Subtree indices */
static gint ett_mcp = -1;
static gint ett_mcp_ws = -1;
static gint ett_mcp_encryption = -1;

/* Preferences */
static guint mcp_port = 8082;

/* MCP Method constants */
typedef struct {
    const char *method;
    const char *description;
} mcp_method_t;

static const mcp_method_t mcp_methods[] = {
    {"initialize", "Initialize MCP connection"},
    {"tools/list", "List available tools"},
    {"resources/list", "List available resources"},
    {"tools/call", "Call a tool"},
    {"resources/read", "Read a resource"},
    {"notifications/initialized", "Connection initialized notification"},
    {"notifications/chess/game_state", "Chess game state notification"},
    {"notifications/chess/ai_move", "AI move notification"},
    {"notifications/chess/training_progress", "Training progress notification"},
    {NULL, NULL}
};

static const mcp_method_t chess_tools[] = {
    {"create_chess_game", "Create new chess game"},
    {"make_chess_move", "Make a chess move"},
    {"get_board_state", "Get current board state"},
    {"analyze_position", "Analyze chess position"},
    {"get_legal_moves", "Get legal moves"},
    {"get_move_hint", "Get move hint"},
    {"create_tournament", "Create tournament"},
    {"get_tournament_status", "Get tournament status"},
    {NULL, NULL}
};

static const mcp_method_t chess_resources[] = {
    {"chess://ai-systems", "AI systems information"},
    {"chess://opening-book", "Opening book database"},
    {"chess://game-history", "Game history"},
    {"chess://training-data", "Training data"},
    {"chess://performance-metrics", "Performance metrics"},
    {NULL, NULL}
};

/* JSON parsing helper structures */
typedef struct {
    char *jsonrpc;
    char *method;
    char *id;
    char *params;
    char *result;
    int error_code;
    char *error_message;
    char *agent_id;
    gboolean encrypted;
    char *ciphertext;
    char *iv;
    char *ratchet_header;
} mcp_json_data_t;

/* Forward declarations */
static int dissect_mcp_websocket(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree, void *data);
static void parse_json_rpc(const char *json_str, mcp_json_data_t *data);
static const char *get_method_description(const char *method);
static void extract_agent_id(const char *params, char *agent_id, size_t agent_id_len);
static void free_mcp_data(mcp_json_data_t *data);

/* Protocol registration */
void proto_register_mcp(void) {
    static hf_register_info hf[] = {
        { &hf_mcp_version, {
            "MCP Version", "mcp.version",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "Model Context Protocol version", HFILL }
        },
        { &hf_mcp_method, {
            "Method", "mcp.method",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "JSON-RPC method name", HFILL }
        },
        { &hf_mcp_id, {
            "Request ID", "mcp.id",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "JSON-RPC request/response ID", HFILL }
        },
        { &hf_mcp_params, {
            "Parameters", "mcp.params",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "Method parameters", HFILL }
        },
        { &hf_mcp_result, {
            "Result", "mcp.result",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "Method result", HFILL }
        },
        { &hf_mcp_error_code, {
            "Error Code", "mcp.error_code",
            FT_INT32, BASE_DEC, NULL, 0x0,
            "JSON-RPC error code", HFILL }
        },
        { &hf_mcp_error_message, {
            "Error Message", "mcp.error_message",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "JSON-RPC error message", HFILL }
        },
        { &hf_mcp_agent_id, {
            "Agent ID", "mcp.agent_id",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "MCP agent identifier", HFILL }
        },
        { &hf_mcp_encrypted, {
            "Encrypted", "mcp.encrypted",
            FT_BOOLEAN, 8, NULL, 0x0,
            "Message is encrypted", HFILL }
        },
        { &hf_mcp_ciphertext, {
            "Ciphertext", "mcp.ciphertext",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "Encrypted message content", HFILL }
        },
        { &hf_mcp_iv, {
            "IV", "mcp.iv",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "Initialization vector", HFILL }
        },
        { &hf_mcp_ratchet_header, {
            "Ratchet Header", "mcp.ratchet_header",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "Double ratchet header", HFILL }
        },
        { &hf_ws_opcode, {
            "WebSocket Opcode", "mcp_ws.opcode",
            FT_UINT8, BASE_DEC, NULL, 0x0,
            "WebSocket frame opcode", HFILL }
        },
        { &hf_ws_fin, {
            "FIN", "mcp_ws.fin",
            FT_BOOLEAN, 8, NULL, 0x0,
            "WebSocket FIN flag", HFILL }
        },
        { &hf_ws_payload_length, {
            "Payload Length", "mcp_ws.payload_length",
            FT_UINT32, BASE_DEC, NULL, 0x0,
            "WebSocket payload length", HFILL }
        },
        { &hf_ws_payload, {
            "WebSocket Payload", "mcp_ws.payload",
            FT_STRING, BASE_NONE, NULL, 0x0,
            "WebSocket frame payload", HFILL }
        }
    };

    static gint *ett[] = {
        &ett_mcp,
        &ett_mcp_ws,
        &ett_mcp_encryption
    };

    proto_mcp = proto_register_protocol("Model Context Protocol", "MCP", "mcp");
    proto_register_field_array(proto_mcp, hf, array_length(hf));
    proto_register_subtree_array(ett, array_length(ett));

    proto_mcp_ws = proto_register_protocol("MCP over WebSocket", "MCP-WS", "mcp_ws");
}

/* Handoff registration */
void proto_reg_handoff_mcp(void) {
    static dissector_handle_t mcp_handle;
    static gboolean initialized = FALSE;

    if (!initialized) {
        mcp_handle = create_dissector_handle(dissect_mcp_websocket, proto_mcp_ws);
        dissector_add_uint("tcp.port", mcp_port, mcp_handle);
        initialized = TRUE;
    }
}

/* Main dissector function */
static int dissect_mcp_websocket(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree, void *data) {
    guint offset = 0;
    guint8 fin, opcode;
    guint16 payload_len_16;
    guint64 payload_len_64;
    guint32 payload_len;
    tvbuff_t *payload_tvb;
    char *payload_str;
    mcp_json_data_t json_data = {0};
    proto_tree *ws_tree, *mcp_tree, *encryption_tree;
    proto_item *ti;

    /* Parse WebSocket frame header */
    if (tvb_captured_length(tvb) < 2) {
        return 0;
    }

    fin = (tvb_get_guint8(tvb, 0) & 0x80) >> 7;
    opcode = tvb_get_guint8(tvb, 0) & 0x0F;
    offset = 1;

    payload_len = tvb_get_guint8(tvb, 1) & 0x7F;
    offset = 2;

    if (payload_len == 126) {
        if (tvb_captured_length(tvb) < offset + 2) return 0;
        payload_len_16 = tvb_get_ntohs(tvb, offset);
        payload_len = payload_len_16;
        offset += 2;
    } else if (payload_len == 127) {
        if (tvb_captured_length(tvb) < offset + 8) return 0;
        payload_len_64 = tvb_get_ntoh64(tvb, offset);
        payload_len = (guint32)payload_len_64;
        offset += 8;
    }

    /* Create WebSocket subtree */
    ws_tree = proto_tree_add_subtree(tree, tvb, 0, offset + payload_len, ett_mcp_ws, NULL, "MCP WebSocket");
    
    ti = proto_tree_add_uint(ws_tree, hf_ws_opcode, tvb, 0, 1, opcode);
    proto_item_append_text(ti, " (%s)", (opcode == 1) ? "Text" : "Binary");

    proto_tree_add_boolean(ws_tree, hf_ws_fin, tvb, 0, 1, fin);
    proto_tree_add_uint(ws_tree, hf_ws_payload_length, tvb, 1, offset - 1, payload_len);

    if (payload_len > 0 && offset + payload_len <= tvb_captured_length(tvb)) {
        payload_tvb = tvb_new_subset_length(tvb, offset, payload_len);
        proto_tree_add_item(ws_tree, hf_ws_payload, payload_tvb, 0, -1, ENC_ASCII);

        /* Only process text frames (opcode 1) */
        if (opcode == 1) {
            payload_str = tvb_get_string_enc(wmem_packet_scope(), payload_tvb, 0, -1, ENC_ASCII);
            
            /* Parse JSON-RPC */
            parse_json_rpc(payload_str, &json_data);
            
            if (json_data.jsonrpc && strcmp(json_data.jsonrpc, "2.0") == 0) {
                /* Create MCP subtree */
                mcp_tree = proto_tree_add_subtree(ws_tree, payload_tvb, 0, -1, ett_mcp, NULL, "Model Context Protocol");

                /* Add basic fields */
                if (json_data.jsonrpc) {
                    proto_tree_add_string(mcp_tree, hf_mcp_version, payload_tvb, 0, 0, json_data.jsonrpc);
                }

                if (json_data.method) {
                    const char *method_desc = get_method_description(json_data.method);
                    ti = proto_tree_add_string(mcp_tree, hf_mcp_method, payload_tvb, 0, 0, json_data.method);
                    if (method_desc) {
                        proto_item_append_text(ti, " (%s)", method_desc);
                    }
                }

                if (json_data.id) {
                    proto_tree_add_string(mcp_tree, hf_mcp_id, payload_tvb, 0, 0, json_data.id);
                }

                /* Add encryption fields if present */
                if (json_data.encrypted) {
                    encryption_tree = proto_tree_add_subtree(mcp_tree, payload_tvb, 0, 0, ett_mcp_encryption, NULL, "Encryption");
                    proto_tree_add_boolean(encryption_tree, hf_mcp_encrypted, payload_tvb, 0, 0, TRUE);
                    
                    if (json_data.ciphertext) {
                        proto_tree_add_string(encryption_tree, hf_mcp_ciphertext, payload_tvb, 0, 0, json_data.ciphertext);
                    }
                    if (json_data.iv) {
                        proto_tree_add_string(encryption_tree, hf_mcp_iv, payload_tvb, 0, 0, json_data.iv);
                    }
                    if (json_data.ratchet_header) {
                        proto_tree_add_string(encryption_tree, hf_mcp_ratchet_header, payload_tvb, 0, 0, json_data.ratchet_header);
                    }
                }

                /* Add params/result/error */
                if (json_data.params) {
                    proto_tree_add_string(mcp_tree, hf_mcp_params, payload_tvb, 0, 0, json_data.params);
                }

                if (json_data.result) {
                    proto_tree_add_string(mcp_tree, hf_mcp_result, payload_tvb, 0, 0, json_data.result);
                }

                if (json_data.error_code != 0) {
                    proto_tree_add_int(mcp_tree, hf_mcp_error_code, payload_tvb, 0, 0, json_data.error_code);
                }

                if (json_data.error_message) {
                    proto_tree_add_string(mcp_tree, hf_mcp_error_message, payload_tvb, 0, 0, json_data.error_message);
                }

                if (json_data.agent_id) {
                    proto_tree_add_string(mcp_tree, hf_mcp_agent_id, payload_tvb, 0, 0, json_data.agent_id);
                }

                /* Set protocol info */
                col_set_str(pinfo->cinfo, COL_PROTOCOL, "MCP");
                if (json_data.method) {
                    col_add_fstr(pinfo->cinfo, COL_INFO, "MCP %s", json_data.method);
                    if (json_data.encrypted) {
                        col_append_str(pinfo->cinfo, COL_INFO, " (Encrypted)");
                    }
                }

                /* Color coding for errors */
                if (json_data.error_code != 0) {
                    col_append_fstr(pinfo->cinfo, COL_INFO, " [ERROR %d]", json_data.error_code);
                }
            }

            /* Clean up */
            free_mcp_data(&json_data);
        }
    }

    return offset + payload_len;
}

/* JSON parsing helper functions */
static void parse_json_rpc(const char *json_str, mcp_json_data_t *data) {
    char *json_copy = wmem_strdup(wmem_packet_scope(), json_str);
    char *pos, *end, *value_start, *value_end;
    char agent_id[256] = {0};

    /* Extract jsonrpc version */
    pos = strstr(json_copy, "\"jsonrpc\"");
    if (pos) {
        pos = strchr(pos, ':');
        if (pos) {
            pos++; /* Skip ':' */
            while (*pos == ' ' || *pos == '\t') pos++; /* Skip whitespace */
            if (*pos == '"') {
                pos++; /* Skip opening quote */
                value_start = pos;
                end = strchr(pos, '"');
                if (end) {
                    *end = '\0';
                    data->jsonrpc = wmem_strdup(wmem_packet_scope(), value_start);
                    *end = '"'; /* Restore */
                }
            }
        }
    }

    /* Extract method */
    pos = strstr(json_copy, "\"method\"");
    if (pos) {
        pos = strchr(pos, ':');
        if (pos) {
            pos++;
            while (*pos == ' ' || *pos == '\t') pos++;
            if (*pos == '"') {
                pos++;
                value_start = pos;
                end = strchr(pos, '"');
                if (end) {
                    *end = '\0';
                    data->method = wmem_strdup(wmem_packet_scope(), value_start);
                    *end = '"';
                }
            }
        }
    }

    /* Extract ID */
    pos = strstr(json_copy, "\"id\"");
    if (pos) {
        pos = strchr(pos, ':');
        if (pos) {
            pos++;
            while (*pos == ' ' || *pos == '\t') pos++;
            if (*pos == '"') {
                pos++;
                value_start = pos;
                end = strchr(pos, '"');
                if (end) {
                    *end = '\0';
                    data->id = wmem_strdup(wmem_packet_scope(), value_start);
                    *end = '"';
                }
            } else {
                /* Numeric ID */
                value_start = pos;
                end = pos;
                while (*end && *end != ',' && *end != '}') end++;
                if (end > value_start) {
                    *end = '\0';
                    data->id = wmem_strdup(wmem_packet_scope(), value_start);
                    *end = (*end == ',') ? ',' : '}';
                }
            }
        }
    }

    /* Check for encryption */
    pos = strstr(json_copy, "\"encrypted\"");
    if (pos) {
        pos = strchr(pos, ':');
        if (pos) {
            pos++;
            while (*pos == ' ' || *pos == '\t') pos++;
            if (strncmp(pos, "true", 4) == 0) {
                data->encrypted = TRUE;
                
                /* Extract ciphertext */
                pos = strstr(json_copy, "\"ciphertext\"");
                if (pos) {
                    pos = strchr(pos, ':');
                    if (pos) {
                        pos++;
                        while (*pos == ' ' || *pos == '\t') pos++;
                        if (*pos == '"') {
                            pos++;
                            value_start = pos;
                            end = strchr(pos, '"');
                            if (end) {
                                *end = '\0';
                                data->ciphertext = wmem_strdup(wmem_packet_scope(), value_start);
                                *end = '"';
                            }
                        }
                    }
                }

                /* Extract IV */
                pos = strstr(json_copy, "\"iv\"");
                if (pos) {
                    pos = strchr(pos, ':');
                    if (pos) {
                        pos++;
                        while (*pos == ' ' || *pos == '\t') pos++;
                        if (*pos == '"') {
                            pos++;
                            value_start = pos;
                            end = strchr(pos, '"');
                            if (end) {
                                *end = '\0';
                                data->iv = wmem_strdup(wmem_packet_scope(), value_start);
                                *end = '"';
                            }
                        }
                    }
                }

                /* Extract ratchet header */
                pos = strstr(json_copy, "\"ratchet_header\"");
                if (pos) {
                    pos = strchr(pos, ':');
                    if (pos) {
                        pos++;
                        while (*pos == ' ' || *pos == '\t') pos++;
                        if (*pos == '{') {
                            value_start = pos;
                            int brace_count = 0;
                            end = pos;
                            while (*end) {
                                if (*end == '{') brace_count++;
                                else if (*end == '}') {
                                    brace_count--;
                                    if (brace_count == 0) {
                                        end++;
                                        break;
                                    }
                                }
                                end++;
                            }
                            if (end > value_start) {
                                *end = '\0';
                                data->ratchet_header = wmem_strdup(wmem_packet_scope(), value_start);
                                *end = '}';
                            }
                        }
                    }
                }
            }
        }
    }

    /* Extract params */
    pos = strstr(json_copy, "\"params\"");
    if (pos) {
        pos = strchr(pos, ':');
        if (pos) {
            pos++;
            while (*pos == ' ' || *pos == '\t') pos++;
            if (*pos == '{') {
                value_start = pos;
                int brace_count = 0;
                end = pos;
                while (*end) {
                    if (*end == '{') brace_count++;
                    else if (*end == '}') {
                        brace_count--;
                        if (brace_count == 0) {
                            end++;
                            break;
                        }
                    }
                    end++;
                }
                if (end > value_start) {
                    *end = '\0';
                    data->params = wmem_strdup(wmem_packet_scope(), value_start);
                    *end = '}';
                    
                    /* Try to extract agent ID from params */
                    extract_agent_id(data->params, agent_id, sizeof(agent_id));
                    if (strlen(agent_id) > 0) {
                        data->agent_id = wmem_strdup(wmem_packet_scope(), agent_id);
                    }
                }
            }
        }
    }

    /* Extract result */
    pos = strstr(json_copy, "\"result\"");
    if (pos) {
        pos = strchr(pos, ':');
        if (pos) {
            pos++;
            while (*pos == ' ' || *pos == '\t') pos++;
            if (*pos == '{') {
                value_start = pos;
                int brace_count = 0;
                end = pos;
                while (*end) {
                    if (*end == '{') brace_count++;
                    else if (*end == '}') {
                        brace_count--;
                        if (brace_count == 0) {
                            end++;
                            break;
                        }
                    }
                    end++;
                }
                if (end > value_start) {
                    *end = '\0';
                    data->result = wmem_strdup(wmem_packet_scope(), value_start);
                    *end = '}';
                }
            }
        }
    }

    /* Extract error code */
    pos = strstr(json_copy, "\"code\"");
    if (pos) {
        pos = strchr(pos, ':');
        if (pos) {
            pos++;
            while (*pos == ' ' || *pos == '\t') pos++;
            if (*pos == '-' || (*pos >= '0' && *pos <= '9')) {
                data->error_code = (int)strtol(pos, &end, 10);
            }
        }
    }

    /* Extract error message */
    pos = strstr(json_copy, "\"message\"");
    if (pos) {
        pos = strchr(pos, ':');
        if (pos) {
            pos++;
            while (*pos == ' ' || *pos == '\t') pos++;
            if (*pos == '"') {
                pos++;
                value_start = pos;
                end = strchr(pos, '"');
                if (end) {
                    *end = '\0';
                    data->error_message = wmem_strdup(wmem_packet_scope(), value_start);
                    *end = '"';
                }
            }
        }
    }
}

static const char *get_method_description(const char *method) {
    int i;
    
    if (!method) return NULL;
    
    /* Check MCP methods */
    for (i = 0; mcp_methods[i].method; i++) {
        if (strcmp(method, mcp_methods[i].method) == 0) {
            return mcp_methods[i].description;
        }
    }
    
    /* Check chess tools */
    for (i = 0; chess_tools[i].method; i++) {
        if (strcmp(method, chess_tools[i].method) == 0) {
            return chess_tools[i].description;
        }
    }
    
    /* Check chess resources */
    for (i = 0; chess_resources[i].method; i++) {
        if (strcmp(method, chess_resources[i].method) == 0) {
            return chess_resources[i].description;
        }
    }
    
    return NULL;
}

static void extract_agent_id(const char *params, char *agent_id, size_t agent_id_len) {
    char *pos, *end;
    
    if (!params || !agent_id || agent_id_len == 0) return;
    
    /* Look for agentId */
    pos = strstr(params, "\"agentId\"");
    if (!pos) {
        pos = strstr(params, "\"agent_id\"");
    }
    
    if (pos) {
        pos = strchr(pos, ':');
        if (pos) {
            pos++;
            while (*pos == ' ' || *pos == '\t') pos++;
            if (*pos == '"') {
                pos++;
                end = strchr(pos, '"');
                if (end && (end - pos) < (int)agent_id_len) {
                    strncpy(agent_id, pos, end - pos);
                    agent_id[end - pos] = '\0';
                }
            }
        }
    }
}

static void free_mcp_data(mcp_json_data_t *data) {
    /* All strings are allocated in wmem_packet_scope, so they'll be freed automatically */
    memset(data, 0, sizeof(mcp_json_data_t));
}
