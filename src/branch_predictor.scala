import chisel3._
import chisel3.util._


//work in progress
class branch_predictor (SIZE_HISTORY: Int, NUMBER_PREDICTOR_ENTRIES: Int, NUMBER_MAX_BRANCH_DEPTH: Int, NUMBER_WORKER: Int) extends Module {

    val io = IO(new Bundle {

		//from decoder
        val is_branch_instruction = Input(Bool())
		val pc = Input (new Bundle {
			val pc_branch = UInt(48.W)
			val else_pc = UInt(48.W)
		})
		
        val return_worker = Input(Vec(NUMBER_WORKER, Valid(new Bundle {
			val depth = UInt(log2ceil(NUMBER_MAX_BRANCH_DEPTH).W)
			val really_taken = Bool()
		})))
        
        val speculative_mode = Output(Bool())

		val misspredict = Output(UInt(log2Ceil(NUMBER_MAX_BRANCH_DEPTH).W)) //les depth inferieurs sont alors aussi invalidés
        
	})


    val history = RegInit(0.U(SIZE_HISTORY.W))
    val predict_tacken = Wire(UInt(1.W))
    val table_prediction = RegInit(VecInit(Seq.fill(NUMBER_PREDICTOR_ENTRIES)(0.U(2.W))))

	val index = UInt(log2Ceil(NUMBER_PREDICTOR_ENTRIES).W)


    val stack_branch_depth = Reg(Vec(NUMBER_MAX_BRANCH_DEPTH, new Bundle {
        val taken = Bool()
        val pc = UInt(48.W)
	}))

    val ptr_stack = RegInit(0.U(UInt(log2Ceil(NUMBER_MAX_BRANCH_DEPTH).W)))


	val max_authorized_branch_depth = RegInit(1.U(UInt(log2Ceil(NUMBER_MAX_BRANCH_DEPTH).W)))

	//gshare + historic

	predict_tacken := table_prediction (index) (1)
	
	stack_branch_depth (ptr_stack) := predict_tacken

    when (io.is_branch_instruction) {
        index := pc (7,0) ^ history (index)
        branch_detected := 1
        ptr_stack := ptr_stack + 1.U
    } 
	
	val ready_entry_historic_update = RegInit(0.U(UInt(log2Ceil(NUMBER_MAX_BRANCH_DEPTH).W)))
    val historic_to_uptade = Reg(UInt(log2Ceil(NUMBER_MAX_BRANCH_DEPTH).W))
	val update_historic = WireInit(false.B)

	update_historic := ready_entry_historic_update (max_authorized_branch_depth, 0).andR
	
	// mise a jour de l'historique in order
//a modifier / affiner l'idée: multi driven ressource
	for (i <- 0 until NUMBER_WORKER) {
		when (io.return_worker.valid) {
			historic_to_uptade (io.return_worker.bits.depth) := really_taken === stack_branch_depth (io.return_worker.depth)
			ready_entry_historic_update (io.return_worker.bits.depth) := 1.U
		} .elsewhen (update_historic) {
			ready_entry_historic_update := 0.U
		}
	}
	

	when (update_historic) {
		historic := historic << max_authorized_branch_depth | historic_to_update
	}
	
	//allocation max de branch depth: voir roles.md Role branch_predictor

	// fsm: IDLE, start predict, get result, scoring + mise a jour du compteur

	//mux fsm

	//mise a jour de max_authorized_branch_depth
	when (update) {
		max_authorized_branch_depth = attibution (score) // attribution est une fonction en fonction de score et qui donne 
		//à partir du score un nombre max de profondeur de branchement authorisé
	}

}

/*
SystemVerilog associe pour BRANCH_DEPTH = 1 et seulement le gshare + historic

logic [HISTORY_WIDTH-1:0] history;
logic [1:0] prediction_counter [0:NUMBER_PREDICTOR_ENTRIES-1];
logic [47:0] branch_pc;
logic [47:0] branch_target_pc;
logic predict_taken;
logic branch_detected;

assign predict_taken = prediction_counter [index];


always_ff @(posedge clk or negedge rst_n) begin
	if (rst_n) begin
		if (is_branch_instruction) begin
			index <= pc [7:0] ^ history;
			branch_detected <= 1;
		end else begin
			branch_detected <= 0;
		end
	end else begin
		branch_detected <= 0;
		index <= 0;
	end
end

always_ff @(posedge clk or negedge rst_n) begin
	if (rst_n) begin

		if (really_taken == predicted_taken) begin
			history <= history << 1 | really_taken;

			if (!(prediction_counter [index] == 2'b11)) begin
				prediction_counter [index] + 1;
			end

		end else begin

			mispredict <= 1;
			history <= history << 1 | really_taken;

			if (!(prediction_counter [index] == 2'b11)) begin
				prediction_counter [index] <= prediction_counter [index] - 1;
			end

		end

	end else begin
		mispredict <= 0;
		for (int i =0; i<NUMBER_PREDICTOR_ENTRIES; i=i+1) begin
			prediction_counter [i] <= 0; //reset obligatoire
		end
	end
end

*/
