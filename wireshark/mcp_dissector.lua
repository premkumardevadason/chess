-- MCP (Model Context Protocol) Wireshark Dissector
-- Analyzes JSON-RPC 2.0 over WebSocket communication for Chess MCP Server
-- Author: AI Assistant
-- Version: 1.0.0

-- Protocol registration
local mcp_protocol = Proto("mcp", "Model Context Protocol (MCP)")
local mcp_websocket_protocol = Proto("mcp_ws", "MCP over WebSocket")

-- Field definitions for MCP protocol
local f_mcp_version = ProtoField.string("mcp.version", "MCP Version", base.ASCII)
local f_mcp_method = ProtoField.string("mcp.method", "Method", base.ASCII)
local f_mcp_id = ProtoField.string("mcp.id", "Request ID", base.ASCII)
local f_mcp_params = ProtoField.string("mcp.params", "Parameters", base.ASCII)
local f_mcp_result = ProtoField.string("mcp.result", "Result", base.ASCII)
local f_mcp_error_code = ProtoField.int32("mcp.error_code", "Error Code", base.DEC)
local f_mcp_error_message = ProtoField.string("mcp.error_message", "Error Message", base.ASCII)
local f_mcp_agent_id = ProtoField.string("mcp.agent_id", "Agent ID", base.ASCII)
local f_mcp_encrypted = ProtoField.bool("mcp.encrypted", "Encrypted", 8)
local f_mcp_ciphertext = ProtoField.string("mcp.ciphertext", "Ciphertext", base.ASCII)
local f_mcp_iv = ProtoField.string("mcp.iv", "IV", base.ASCII)
local f_mcp_ratchet_header = ProtoField.string("mcp.ratchet_header", "Ratchet Header", base.ASCII)

-- Field definitions for WebSocket layer
local f_ws_opcode = ProtoField.uint8("mcp_ws.opcode", "WebSocket Opcode", base.DEC)
local f_ws_fin = ProtoField.bool("mcp_ws.fin", "FIN", 8)
local f_ws_payload_length = ProtoField.uint32("mcp_ws.payload_length", "Payload Length", base.DEC)
local f_ws_payload = ProtoField.string("mcp_ws.payload", "WebSocket Payload", base.ASCII)

-- Register fields
mcp_protocol.fields = {
    f_mcp_version, f_mcp_method, f_mcp_id, f_mcp_params, f_mcp_result,
    f_mcp_error_code, f_mcp_error_message, f_mcp_agent_id, f_mcp_encrypted,
    f_mcp_ciphertext, f_mcp_iv, f_mcp_ratchet_header
}

mcp_websocket_protocol.fields = {
    f_ws_opcode, f_ws_fin, f_ws_payload_length, f_ws_payload
}

-- MCP method constants
local MCP_METHODS = {
    ["initialize"] = "Initialize MCP connection",
    ["tools/list"] = "List available tools",
    ["resources/list"] = "List available resources", 
    ["tools/call"] = "Call a tool",
    ["resources/read"] = "Read a resource",
    ["notifications/initialized"] = "Connection initialized notification",
    ["notifications/chess/game_state"] = "Chess game state notification",
    ["notifications/chess/ai_move"] = "AI move notification",
    ["notifications/chess/training_progress"] = "Training progress notification"
}

-- Chess-specific tools
local CHESS_TOOLS = {
    ["create_chess_game"] = "Create new chess game",
    ["make_chess_move"] = "Make a chess move",
    ["get_board_state"] = "Get current board state",
    ["analyze_position"] = "Analyze chess position",
    ["get_legal_moves"] = "Get legal moves",
    ["get_move_hint"] = "Get move hint",
    ["create_tournament"] = "Create tournament",
    ["get_tournament_status"] = "Get tournament status"
}

-- Chess resources
local CHESS_RESOURCES = {
    ["chess://ai-systems"] = "AI systems information",
    ["chess://opening-book"] = "Opening book database",
    ["chess://game-history"] = "Game history",
    ["chess://training-data"] = "Training data",
    ["chess://performance-metrics"] = "Performance metrics"
}

-- AI systems
local AI_SYSTEMS = {
    ["alphazero"] = "AlphaZero",
    ["leela"] = "Leela Chess Zero",
    ["stockfish"] = "Stockfish",
    ["monte_carlo"] = "Monte Carlo Tree Search",
    ["minimax"] = "Minimax with Alpha-Beta",
    ["neural_network"] = "Neural Network",
    ["genetic_algorithm"] = "Genetic Algorithm",
    ["q_learning"] = "Q-Learning",
    ["deep_q_network"] = "Deep Q-Network",
    ["a3c"] = "A3C (Actor-Critic)",
    ["alphafold3"] = "AlphaFold3",
    ["cnn"] = "Convolutional Neural Network"
}

-- Helper function to parse JSON (simplified)
local function parse_json_simple(json_str)
    local result = {}
    
    -- Extract jsonrpc version
    local version = string.match(json_str, '"jsonrpc"%s*:%s*"([^"]*)"')
    if version then
        result.jsonrpc = version
    end
    
    -- Extract method
    local method = string.match(json_str, '"method"%s*:%s*"([^"]*)"')
    if method then
        result.method = method
    end
    
    -- Extract ID
    local id = string.match(json_str, '"id"%s*:%s*([^,}]+)')
    if id then
        result.id = id:gsub('"', '')
    end
    
    -- Check if encrypted
    local encrypted = string.match(json_str, '"encrypted"%s*:%s*(true)')
    if encrypted then
        result.encrypted = true
        
        -- Extract encryption fields
        local ciphertext = string.match(json_str, '"ciphertext"%s*:%s*"([^"]*)"')
        if ciphertext then
            result.ciphertext = ciphertext
        end
        
        local iv = string.match(json_str, '"iv"%s*:%s*"([^"]*)"')
        if iv then
            result.iv = iv
        end
        
        local ratchet_header = string.match(json_str, '"ratchet_header"%s*:%s*({[^}]*})')
        if ratchet_header then
            result.ratchet_header = ratchet_header
        end
    end
    
    -- Extract params (simplified)
    local params_start = string.find(json_str, '"params"%s*:%s*{')
    if params_start then
        local brace_count = 0
        local params_end = params_start
        for i = params_start, #json_str do
            local char = string.sub(json_str, i, i)
            if char == '{' then
                brace_count = brace_count + 1
            elseif char == '}' then
                brace_count = brace_count - 1
                if brace_count == 0 then
                    params_end = i
                    break
                end
            end
        end
        result.params = string.sub(json_str, params_start, params_end)
    end
    
    -- Extract result (simplified)
    local result_start = string.find(json_str, '"result"%s*:%s*{')
    if result_start then
        local brace_count = 0
        local result_end = result_start
        for i = result_start, #json_str do
            local char = string.sub(json_str, i, i)
            if char == '{' then
                brace_count = brace_count + 1
            elseif char == '}' then
                brace_count = brace_count - 1
                if brace_count == 0 then
                    result_end = i
                    break
                end
            end
        end
        result.result = string.sub(json_str, result_start, result_end)
    end
    
    -- Extract error
    local error_code = string.match(json_str, '"code"%s*:%s*(-?%d+)')
    if error_code then
        result.error_code = tonumber(error_code)
    end
    
    local error_message = string.match(json_str, '"message"%s*:%s*"([^"]*)"')
    if error_message then
        result.error_message = error_message
    end
    
    return result
end

-- Helper function to get method description
local function get_method_description(method)
    if MCP_METHODS[method] then
        return MCP_METHODS[method]
    elseif CHESS_TOOLS[method] then
        return "Chess Tool: " .. CHESS_TOOLS[method]
    elseif CHESS_RESOURCES[method] then
        return "Chess Resource: " .. CHESS_RESOURCES[method]
    else
        return "Unknown Method"
    end
end

-- Helper function to extract agent ID from params
local function extract_agent_id(params_str)
    if not params_str then return nil end
    
    -- Look for agentId in various possible locations
    local agent_id = string.match(params_str, '"agentId"%s*:%s*"([^"]*)"')
    if agent_id then return agent_id end
    
    agent_id = string.match(params_str, '"agent_id"%s*:%s*"([^"]*)"')
    if agent_id then return agent_id end
    
    return nil
end

-- Main dissector function
local function dissect_mcp_websocket(buffer, pinfo, tree)
    local offset = 0
    local buffer_len = buffer:len()
    
    if buffer_len < 2 then
        return 0
    end
    
    -- Parse WebSocket frame header
    local fin = bit.band(buffer(offset, 1):uint(), 0x80) ~= 0
    local opcode = bit.band(buffer(offset, 1):uint(), 0x0F)
    offset = offset + 1
    
    local payload_len = bit.band(buffer(offset, 1):uint(), 0x7F)
    offset = offset + 1
    
    if payload_len == 126 then
        if buffer_len < offset + 2 then return 0 end
        payload_len = buffer(offset, 2):uint()
        offset = offset + 2
    elseif payload_len == 127 then
        if buffer_len < offset + 8 then return 0 end
        payload_len = buffer(offset, 8):uint()
        offset = offset + 8
    end
    
    -- Create WebSocket subtree
    local ws_tree = tree:add(mcp_websocket_protocol, buffer(0, offset + payload_len))
    ws_tree:add(f_ws_opcode, buffer(0, 1))
    ws_tree:add(f_ws_fin, buffer(0, 1))
    ws_tree:add(f_ws_payload_length, buffer(1, offset - 1))
    
    if payload_len > 0 and offset + payload_len <= buffer_len then
        local payload = buffer(offset, payload_len)
        ws_tree:add(f_ws_payload, payload)
        
        -- Only process text frames (opcode 1)
        if opcode == 1 then
            local payload_str = payload:string()
            
            -- Try to parse as JSON-RPC
            local json_data = parse_json_simple(payload_str)
            
            if json_data.jsonrpc == "2.0" then
                -- Create MCP subtree
                local mcp_tree = ws_tree:add(mcp_protocol, payload)
                
                -- Add basic fields
                if json_data.jsonrpc then
                    mcp_tree:add(f_mcp_version, json_data.jsonrpc)
                end
                
                if json_data.method then
                    local method_desc = get_method_description(json_data.method)
                    mcp_tree:add(f_mcp_method, json_data.method):append_text(" (" .. method_desc .. ")")
                end
                
                if json_data.id then
                    mcp_tree:add(f_mcp_id, json_data.id)
                end
                
                -- Add encryption fields if present
                if json_data.encrypted then
                    mcp_tree:add(f_mcp_encrypted, true)
                    if json_data.ciphertext then
                        mcp_tree:add(f_mcp_ciphertext, json_data.ciphertext)
                    end
                    if json_data.iv then
                        mcp_tree:add(f_mcp_iv, json_data.iv)
                    end
                    if json_data.ratchet_header then
                        mcp_tree:add(f_mcp_ratchet_header, json_data.ratchet_header)
                    end
                end
                
                -- Add params/result/error
                if json_data.params then
                    mcp_tree:add(f_mcp_params, json_data.params)
                    
                    -- Try to extract agent ID
                    local agent_id = extract_agent_id(json_data.params)
                    if agent_id then
                        mcp_tree:add(f_mcp_agent_id, agent_id)
                    end
                end
                
                if json_data.result then
                    mcp_tree:add(f_mcp_result, json_data.result)
                end
                
                if json_data.error_code then
                    mcp_tree:add(f_mcp_error_code, json_data.error_code)
                end
                
                if json_data.error_message then
                    mcp_tree:add(f_mcp_error_message, json_data.error_message)
                end
                
                -- Set protocol info
                pinfo.cols.protocol = "MCP"
                if json_data.method then
                    pinfo.cols.info = "MCP " .. json_data.method
                    if json_data.encrypted then
                        pinfo.cols.info = pinfo.cols.info .. " (Encrypted)"
                    end
                end
                
                -- Color coding
                if json_data.error_code then
                    pinfo.cols.info = pinfo.cols.info .. " [ERROR " .. json_data.error_code .. "]"
                end
            end
        end
    end
    
    return offset + payload_len
end

-- Register the dissector
function mcp_websocket_protocol.dissector(buffer, pinfo, tree)
    return dissect_mcp_websocket(buffer, pinfo, tree)
end

-- Register for WebSocket protocol
local ws_dissector = Dissector.get("websocket")
if ws_dissector then
    local original_dissector = ws_dissector
    function ws_dissector:call(buffer, pinfo, tree)
        -- Check if this looks like MCP traffic
        local payload = buffer:raw()
        if payload and #payload > 0 then
            local payload_str = payload:gsub("%z", "")
            if string.find(payload_str, '"jsonrpc"%s*:%s*"2%.0"') and 
               (string.find(payload_str, '"method"%s*:%s*"initialize"') or
                string.find(payload_str, '"method"%s*:%s*"tools/') or
                string.find(payload_str, '"method"%s*:%s*"resources/') or
                string.find(payload_str, '"result"') or
                string.find(payload_str, '"error"')) then
                
                return dissect_mcp_websocket(buffer, pinfo, tree)
            end
        end
        
        -- Fall back to original WebSocket dissector
        return original_dissector:call(buffer, pinfo, tree)
    end
end

-- Register for TCP port 8082 (default MCP WebSocket port)
local tcp_port = Dissector.get("tcp.port")
tcp_port:add(8082, mcp_websocket_protocol)
tcp_port:add(8083, mcp_websocket_protocol) -- Alternative port

print("MCP Wireshark Dissector loaded successfully!")
