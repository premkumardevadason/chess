# CMakeLists.txt for MCP Wireshark Plugin
cmake_minimum_required(VERSION 3.10)
project(mcp_dissector)

# Find Wireshark development files
find_package(PkgConfig REQUIRED)
pkg_check_modules(WIRESHARK REQUIRED wireshark)

# Include directories
include_directories(${WIRESHARK_INCLUDE_DIRS})

# Create the dissector library
add_library(mcp_dissector SHARED
    mcp_dissector.c
)

# Link libraries
target_link_libraries(mcp_dissector ${WIRESHARK_LIBRARIES})

# Compiler flags
target_compile_options(mcp_dissector PRIVATE ${WIRESHARK_CFLAGS_OTHER})

# Install the plugin
install(TARGETS mcp_dissector
    LIBRARY DESTINATION lib/wireshark/plugins/${WIRESHARK_VERSION}
)

# Set properties
set_target_properties(mcp_dissector PROPERTIES
    PREFIX ""
    SUFFIX ".so"
)
