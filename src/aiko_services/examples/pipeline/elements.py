# Usage
# ~~~~~
# cd ../examples/pipeline
# aiko_pipeline create pipeline_local.json
# aiko_pipeline create pipeline_remote.json
# aiko_pipeline create pipeline_test.json
#
# aiko_pipeline create pipeline_example.json -s 1 -p limit 1000 -p rate 1
# aiko_dashboard  # select "pe_randomintegers" and watch "random" update
#
# aiko_pipeline create pipeline_paths.json -gp PE_IN_0 -fd "(in_a: x)"
#
# TOPIC=$NAMESPACE/$HOST/$PID/$SID/in
# mosquitto_pub -h $HOST -t $TOPIC -m "(create_stream 1)"
# mosquitto_pub -h $HOST -t $TOPIC -m "(process_frame (stream_id: 1) (a: 0))"
# mosquitto_pub -h $HOST -t $TOPIC -m "(destroy_stream 1)"
#
# To Do
# ~~~~~
# - PE_Metrics: Make visible to Aiko Dashboard via self.share[]
# - PE_Metrics: Store to file (JSON, CSV), SQLite, InfluxDB
# - PE_Metrics: Add run-time average calculation
#
# - Consider PE_DataDecode and PE_DataEncode using "kwargs" for flexible
#   choices of data type to transfer via function parameters

import base64
from io import BytesIO
import numpy as np
from typing import Tuple

import aiko_services as aiko
from aiko_services.main.utilities import parse

# --------------------------------------------------------------------------- #

def _all_outputs(pipeline_element, stream):
    frame = stream.frames[stream.frame_id]
    outputs = {}
    for output_definition in pipeline_element.definition.output:
        output_name = output_definition["name"]
        if output_name in frame.swag:
            outputs[output_name] = frame.swag[output_name]
    return outputs

# --------------------------------------------------------------------------- #

def _all_options(pipeline_element, stream):
    """ this function returns all of the options
    There doesn't seem to be a way to directly access the pipeline_definiton from the pipeline element
    -- can do this via the context object that is passed to the pipeline element init method (but this isn't saved)
    -- the pipeline_defintion_path is saved so might be able to reparse the file and create a new definition object
    is this the best way to do this?

    Option element is defined as as a PipelineElement with a parameter given by the selector value in the PE_SELECT element
    This defaults to "option" but can be changed to any value
    
    """

    def get_selector(pipeline_definition):
        for element in pipeline_definition.elements:
            if element.deploy.class_name == "PE_SELECT":
                selector = element.parameters.get("selector", "option")
                return selector
        return "option"
    
    options = []
    
    pipeline_definition = pipeline_element.pipeline.definition
    selector = get_selector(pipeline_definition)

    for element in pipeline_definition.elements:
        if selector in element.parameters:            
            option_name = element.name
            options.append(option_name)
    return options

import time

class PE_Add(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("add:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, i) -> Tuple[aiko.StreamEvent, dict]:
        constant, _ = self.get_parameter("constant", default=1)
        i_new = int(i) + int(constant)

        self.logger.info(f"{self.my_id()} i in: {i}, out: {i_new}")

        delay, _ = self.get_parameter("delay", default=0)  # seconds
        if delay:
            time.sleep(float(delay))

        return aiko.StreamEvent.OKAY, {"i": i_new}

# --------------------------------------------------------------------------- #

class PE_Inspect(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("inspect:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def _get_inspect_file(self, stream, target):
        inspect_file = stream.variables.get("inspect_file", None)
        if not inspect_file:
            _, inspect_filepath = target.split(":")
            inspect_file = open(inspect_filepath, "a")
            stream.variables["inspect_file"] = inspect_file
        return inspect_file

    def process_frame(self, stream) -> Tuple[aiko.StreamEvent, dict]:
        frame = stream.frames[stream.frame_id]

        enable, _ = self.get_parameter("enable", True)
        if enable:
            names, found = self.get_parameter("inspect")
            if found:
                name, names = parse(names)
                names.insert(0, name)
                if "*" in names:
                    names = frame.swag.keys()
            else:
                names = frame.swag.keys()

            target, _ = self.get_parameter("target", "log")
            if target.startswith("file:"):
                inspect_file = self._get_inspect_file(stream, target)

            for name in names:
                value = frame.swag.get(name, None)
                name_value = f"{self.my_id()} {name}: {value}"

                if target.startswith("file:"):
                    inspect_file.write(name_value + "\n")
                elif target == "log":
                    self.logger.info(name_value)
                elif target == "print":
                    print(name_value)
                else:
                    diagnostic =  \
                        "'target' parameter must be 'file', 'log' or 'print'"
                    return aiko.StreamEvent.ERROR, {"diagnostic": dianostic}

            if target.startswith("file:"):
                inspect_file.flush()

        return aiko.StreamEvent.OKAY, _all_outputs(self, stream)

    def stop_stream(self, stream, stream_id):
        inspect_file = stream.variables.get("inspect_file", None)
        if inspect_file:
            inspect_file.close()
        return aiko.StreamEvent.OKAY, {}

# --------------------------------------------------------------------------- #
# PE_Metrics typically appears at the end of a Pipeline graph.
# So that child Pipeline responses can be returned to the parent Pipeline,
# the PE_Metrics PipelineElement Definition can refer to any output
# produced by the prior PipelineElements in the Pipeline graph
#
# For example ... "output": [{ "name": "i", "type": "int" }]

class PE_Metrics(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("metrics:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream) -> Tuple[aiko.StreamEvent, dict]:
        enable, _ = self.get_parameter("enable", True)
        rate, _ = self.get_parameter("rate", default=1)
        if not enable or stream.frame_id % rate != 0:
            return aiko.StreamEvent.OKAY, _all_outputs(self, stream)

        frame = stream.frames[stream.frame_id]
        metrics = frame.metrics
        metrics_elements = metrics["elements"]
        for name, value in metrics_elements.items():
            if name.endswith("_memory"):
                value /= 1000000
                self.logger.debug(f"{self.my_id()} "
                    f"{name:22s}: {value:.3f} Mb+")
            if name.endswith("_time"):
                value *= 1000
                self.logger.debug(f"{self.my_id()} "
                    f"{name:22s}: {value:.3f} ms")

        if "pipeline_time" in metrics:
            pipeline_time = metrics["pipeline_time"] * 1000
            self.logger.debug(f"{self.my_id()} "
                f"{'Pipeline time':22s}: {pipeline_time:.3f} ms")

        if "pipeline_memory" in metrics:
            pipeline_memory = metrics["pipeline_memory"] / 1000000
            self.logger.debug(f"{self.my_id()} "
                f"{'Pipeline memory':22s}: {pipeline_memory:.3f} Mb+")

        if "pipeline_start_memory" in metrics:
            process_memory = metrics["pipeline_start_memory"] / 1000000
            self.logger.debug(f"{self.my_id()} "
                f"{'Process  memory':22s}: {process_memory:.3f} Mb")

        return aiko.StreamEvent.OKAY, _all_outputs(self, stream)

# --------------------------------------------------------------------------- #

import random

class PE_RandomIntegers(aiko.PipelineElement):
    def __init__(self, context: aiko.ContextPipelineElement):
        context.set_protocol("random_integers:0")  # data_source:0
        context.get_implementation("PipelineElement").__init__(self, context)
        self.share["random"] = "?"

    def start_stream(self, stream, stream_id):
        rate, _ = self.get_parameter("rate", default=1.0)
        self.create_frames(stream, self.frame_generator, rate=float(rate))
        return aiko.StreamEvent.OKAY, {}

    def frame_generator(self, stream, frame_id):
        limit, _ = self.get_parameter("limit")
        if frame_id < int(limit):
            frame_data = {"random": random.randint(0, 9)}

        # Create several frames at once
        #   frame_data = []
        #   for _ in range(3):
        #       frame_data.append({"random": random.randint(0, 9)})

            return aiko.StreamEvent.OKAY, frame_data
        else:
            return aiko.StreamEvent.STOP, {"diagnostic": "Frame limit reached"}

    def process_frame(self, stream, random) -> Tuple[aiko.StreamEvent, dict]:
        self.logger.info(f"{self.my_id()} random: {random}")
        self.ec_producer.update("random", random)
        return aiko.StreamEvent.OKAY, {"random": random}

# --------------------------------------------------------------------------- #

class PE_0(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("increment:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, a) -> Tuple[aiko.StreamEvent, dict]:
        pe_0_inc, _ = self.get_parameter("pe_0_inc", 1)
        b = int(a) + int(pe_0_inc)
        self.logger.info(f"{self.my_id()} in a: {a}, out b: {b}")
        return aiko.StreamEvent.OKAY, {"b": b}

# --------------------------------------------------------------------------- #

class PE_1(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("increment:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, b) -> Tuple[aiko.StreamEvent, dict]:
        increment = 1
        p_1, _ = self.get_parameter("p_1")
        pe_1_inc, _ = self.get_parameter("pe_1_inc", 1)
        c = int(b) + int(pe_1_inc)
        self.logger.info(f"{self.my_id()} in b: {b}, out c: {c}")
        self.logger.info(f"      parameter pe_1_inc: {pe_1_inc}")
        return aiko.StreamEvent.OKAY, {"c": c}

# --------------------------------------------------------------------------- #

class PE_2(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("increment:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, c) -> Tuple[aiko.StreamEvent, dict]:
        d = int(c) + 1
        self.logger.info(f"{self.my_id()} in c: {c}, out d: {d}")
        return aiko.StreamEvent.OKAY, {"d": d}

# --------------------------------------------------------------------------- #

class PE_3(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("increment:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, c) -> Tuple[aiko.StreamEvent, dict]:
        e = int(c) + 1
        self.logger.info(f"{self.my_id()} in c: {c}, out e: {e}")
        return aiko.StreamEvent.OKAY, {"e": e}

# --------------------------------------------------------------------------- #

class PE_4(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("sum:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, d, e) -> Tuple[aiko.StreamEvent, dict]:
        f = int(d) + int(e)
        self.logger.info(f"{self.my_id()} in d: {d}, e: {e}, out: d + e = f: {f}")
        return aiko.StreamEvent.OKAY, {"f": f}

# --------------------------------------------------------------------------- #
# Choose Pipeline Graph Path (see pipeline_paths.json)
# ~~~~~~~~~~~~~~~~~~~~~~~~~~
# For the Pipeline or override for a specific Stream
#
# aiko_pipeline create pipeline_paths.json -gp PE_IN_0 -fd "(in_a: x)"
# aiko_pipeline create pipeline_paths.json -gp PE_IN_1 -fd "(in_a: x)"
#
# mosquitto_pub -t $TOPIC_PATH/in -m "(create_stream 1 PE_IN_1)"
# mosquitto_pub -t $TOPIC_PATH/in -m "(process_frame (stream_id: 1) (in_a: x))"
# mosquitto_pub -t $TOPIC_PATH/in -m "(destroy_stream 1)"

class PE_IN(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("in:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, in_a) -> Tuple[aiko.StreamEvent, dict]:
        text_b = f"{in_a}:in"
        self.logger.info(f"<---STREAM: {self.my_id()} {'-'*20}->")
        self.logger.info(f"{self.my_id()} out: {text_b} <-- in: {in_a}")
        return aiko.StreamEvent.OKAY, {"text_b": text_b}

# --------------------------------------------------------------------------- #

class PE_TEXT(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("text_to_text:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, text_b) -> Tuple[aiko.StreamEvent, dict]:
        text_b = f"{text_b}:text"
        self.logger.info(f"{self.my_id()} out: {text_b}")
        return aiko.StreamEvent.OKAY, {"text_b": text_b}
    

class PE_SELECT(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("select:0")
        context.get_implementation("PipelineElement").__init__(self, context)
        

    def process_frame(self, stream, text_b) -> Tuple[aiko.StreamEvent, dict]:
        # this creates a frame for each option with the frame data from the current frame

        # try to discover the options that are available in the pipeline
        options = _all_options(self, stream)
        self.logger.info(f"{self.my_id()} available options: {options}")

        # get the step that each of the options should then go to, again a parameter to this select
        next_step, _ = self.get_parameter("next_step", default="PE_OUT_0")

        selection = [opt for opt in options if random.random() > 0.5]
        self.logger.info(f"{self.my_id()} selected options: {selection}")
        stream.variables["option_selection"] = selection

        # create a frame for each option
        for option in selection:
            frame_data = {}
            frame_data["option"] = option
            frame_data["text_b"] = text_b
            frame_data["next_step"] = next_step
            self.create_frame(stream, frame_data=frame_data, graph_path=option)
            self.logger.info(f"{self.my_id()} created frame for {option} with framedata: {frame_data}")
        
        self.logger.info(f"{self.my_id()} out: {text_b}")
        return aiko.StreamEvent.OKAY, {"text_b": text_b}  
    
class PE_OPTION(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("option:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, text_b) -> Tuple[aiko.StreamEvent, dict]:
        frame = stream.frames[stream.frame_id]
        # put the results into the stream variable
        if "option" not in stream.variables:
            stream.variables["option"] = {}

        # simulate some processing time
        time.sleep(random.randint(1, 10))
        
        out_c = f"{frame.swag['option']}:{text_b}:out"
        stream.variables["option"][frame.swag["option"]] = out_c

        # create a new frame to pass back to final element
        self.logger.debug(f"CREATING NEXT STEP FRAME FOR {frame.swag['option']}")
        self.logger.debug(f"stream variables['option']: {stream.variables['option']}")
        self.create_frame(stream, frame_data=frame.swag, graph_path=frame.swag["next_step"])

        self.logger.info(f"{self.my_id()} out: {out_c}")
        return aiko.StreamEvent.OKAY, {"out_c": out_c}

# --------------------------------------------------------------------------- #

class PE_DYNAMIC_OUT(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("out:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, text_b, out_c) -> Tuple[aiko.StreamEvent, dict]:
        options = stream.variables["option_selection"]
        self.logger.info(f"PE_DYNAMIC_OUT: out_c: {out_c}")

        # add the check for the option completion variable
        if "option_completion" not in stream.variables:
            stream.variables["option_completion"] = {}
            
        option_results = stream.variables["option"]

        # simply compare two list to see if they are the same
        if len(set(options).difference(set(option_results.keys()))) == 0:
            stream.variables["option_completion"] = True
            self.logger.info(f"{self.my_id()} ALL options completed out: {option_results}")
        else:
            self.logger.info(f"{self.my_id()} NOT all options have completed: {option_results} ")
        
        self.logger.info(f"OUT_FINAL: {self.my_id()} out: {stream.variables['option']}")
        return aiko.StreamEvent.OKAY, {}

# --------------------------------------------------------------------------- #

class PE_OUT(aiko.PipelineElement):
    def __init__(self, context):
        context.set_protocol("out:0")
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, text_b) -> Tuple[aiko.StreamEvent, dict]:
        out_c = f"{text_b}:out"
        self.logger.info(f"{self.my_id()} out: {out_c}")
        return aiko.StreamEvent.OKAY, {"out_c": out_c}

# --------------------------------------------------------------------------- #

class PE_DataDecode(aiko.PipelineElement):
    def __init__(self, context):
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, data) -> Tuple[aiko.StreamEvent, dict]:
        data = base64.b64decode(data.encode("utf-8"))
        np_bytes = BytesIO(data)
        data = np.load(np_bytes, allow_pickle=True)
    #   self.logger.info(f"{self.my_id()} data: {data}")
        return aiko.StreamEvent.OKAY, {"data": data}

# --------------------------------------------------------------------------- #

class PE_DataEncode(aiko.PipelineElement):
    def __init__(self, context):
        context.get_implementation("PipelineElement").__init__(self, context)

    def process_frame(self, stream, data) -> Tuple[aiko.StreamEvent, dict]:
    #   self.logger.info(f"{self.my_id()} data: {data}")
        if isinstance(data, str):
            data = str.encode(data)
        if isinstance(data, np.ndarray):
            np_bytes = BytesIO()
            np.save(np_bytes, data, allow_pickle=True)
            data = np_bytes.getvalue()
        data = base64.b64encode(data).decode("utf-8")
        return aiko.StreamEvent.OKAY, {"data": data}

# --------------------------------------------------------------------------- #
