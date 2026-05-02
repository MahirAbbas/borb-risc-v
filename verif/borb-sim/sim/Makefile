TOP=SoC
VERILOG=../../../SoC.v
BUILD_DIR=../build
OBJ_DIR=$(BUILD_DIR)/obj_dir

CPU_COUNT := $(shell getconf _NPROCESSORS_ONLN 2>/dev/null || nproc 2>/dev/null || echo 1)

FAST ?= 1
TRACE ?= 0
THREADS ?= 1
VERILATE_JOBS ?= $(CPU_COUNT)
OUTPUT_SPLIT ?= 20000
CXXFLAGS=-O3 -std=c++17
VERBOSE ?= 0
VERILATOR_FLAGS :=

ifeq ($(VERBOSE),1)
Q :=
SUBMAKE_SILENT :=
VERILATOR_STDOUT :=
else
Q := @
SUBMAKE_SILENT := -s
VERILATOR_STDOUT := >/dev/null
endif

ifeq ($(FAST),1)
CXXFLAGS += -DNDEBUG -march=native -mtune=native
TRACE := 0
endif

ifeq ($(TRACE),1)
VERILATOR_TRACE_FLAG := --trace-fst
CXXFLAGS += -DBORB_TRACE_FST=1
else
VERILATOR_TRACE_FLAG :=
endif

ifeq ($(THREADS),1)
VERILATOR_THREADS_FLAG :=
else
VERILATOR_THREADS_FLAG := --threads $(THREADS)
endif

ifeq ($(VERILATE_JOBS),1)
VERILATOR_JOBS_FLAG :=
else
VERILATOR_JOBS_FLAG := --verilate-jobs $(VERILATE_JOBS)
endif

all: $(BUILD_DIR)/borb-sim

$(BUILD_DIR)/borb-sim: borb_sim.cpp $(VERILOG)
	$(Q)mkdir -p $(BUILD_DIR) $(OBJ_DIR)
	$(Q)verilator -cc $(VERILOG) --top-module $(TOP) --exe borb_sim.cpp \
		--Mdir $(OBJ_DIR) $(VERILATOR_TRACE_FLAG) $(VERILATOR_THREADS_FLAG) $(VERILATOR_JOBS_FLAG) \
		--output-split $(OUTPUT_SPLIT) --output-split-cfuncs $(OUTPUT_SPLIT) \
		-Wno-UNSIGNED -Wno-UNOPTTHREADS -CFLAGS "$(CXXFLAGS)" $(VERILATOR_FLAGS) $(VERILATOR_STDOUT)
	$(Q)$(MAKE) $(SUBMAKE_SILENT) -C $(OBJ_DIR) -f V$(TOP).mk
	$(Q)cp $(OBJ_DIR)/V$(TOP) $(BUILD_DIR)/borb-sim

clean:
	$(Q)rm -rf $(BUILD_DIR)

.PHONY: all clean
