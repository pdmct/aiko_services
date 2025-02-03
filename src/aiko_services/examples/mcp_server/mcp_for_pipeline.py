## example that creates an MCP server that creates a pipeline and
## then responds to requests to call the pipeline
import re
import sys
import os
import logging
import json
import queue
import signal
from threading import Thread
from typing import Any, Dict, Optional
import functools
import inspect

import httpx
from mcp.server.fastmcp import FastMCP

import aiko_services as aiko

logger = logging.getLogger(__name__)

# Initialize FastMCP server
mcp = FastMCP("aiko_pipeline_server")

# Constants
PIPELINE_DEFINITION_FILES = "./src/aiko_services/examples/pipeline"
PIPELINE_DEFINITION = "pipeline_mcp.json"
PIPELINE_QUEUE_TIMEOUT = 10

DEFAULT_STREAM_ID = 1
# globals
frame_id = 0
stream_id = DEFAULT_STREAM_ID


class Assistant:
    def __init__(self, context, pipeline_definition_pathname):
        self.queue = queue.Queue()
        self.frame_count = 0
        self.pipeline_definition_pathname = pipeline_definition_pathname
        self.pipeline = aiko.PipelineImpl.create_pipeline(
            definition_pathname=pipeline_definition_pathname,
            pipeline_definition=aiko.PipelineImpl.parse_pipeline_definition(pipeline_definition_pathname),
            name=Assistant.get_pipeline_name(pipeline_definition_pathname),
            graph_path=None,
            stream_id=DEFAULT_STREAM_ID,
            parameters={},
            frame_id=0,
            frame_data={},
            grace_time=300,
            queue_response=self.queue,
        )
        run_args = {"mqtt_connection_required": False}
        pipeline_thread = Thread(target=aiko.process.run, kwargs=run_args)
        pipeline_thread.start()

    @staticmethod
    def terminate():
        aiko.process.terminate()

    @staticmethod
    def get_pipeline_name(pipeline_definition_pathname: str) -> str:
        with open(pipeline_definition_pathname, "r") as file:
            pipeline_name = json.load(file)["name"][2:]  # TODO: Don't truncate
        return pipeline_name
    
    def invoke_graph(
            self,
            start_node: str,
            stream_id: Optional[str] = DEFAULT_STREAM_ID,
            parameters: Optional[Dict[str, Any]] = None, # any pipeline parameters
            kwargs: Optional[Dict[str, Any]] = None, # any arguments to pass to the pipeline
    ) -> Dict[str, Any]:
        """ Generic method to invoke a graph in the pipeline

        :param start_node: The start ode to select the grapoh path
        :type start_node: str
        :param stream_id:  defaults to DEFAULT_STREAM_ID
        :type stream_id: Optional[str], optional
        :param parameters: any pipeline parameters, defaults to None
        :type parameters: Optional[Dict[str, Any]], optional
        :param kwargs: any arguments to pass to the pipeline, defaults to None
        :type kwargs: Optional[Dict[str, Any]], optional
        :return:  The response from the pipeline
        :rtype: Dict[str, Any]
        """
        logger.info(f"Invoking graph: {start_node} with stream_id: {stream_id} and kwargs: {kwargs}")
        try:
            # Stream setup
            stream = {"stream_id": stream_id, "frame_id": self.frame_count}
            self.frame_count += 1
            self.pipeline.create_stream(
                stream_id=stream_id,
                graph_path=start_node,
                parameters=parameters if parameters else {},
                grace_time=300,
                queue_response=self.queue,
            )



            # Frame data setup and queue processing
            frame_data = kwargs if kwargs else {}
            self.pipeline.create_frame(stream, frame_data)
            response = self.queue.get(timeout=PIPELINE_QUEUE_TIMEOUT)
        except queue.Empty:
            response = {"error": "Pipeline queue timed-out waiting for response"}
        except Exception as exception:
            logger.error(f"ERROR: invoke_graph: {exception}")
            response = {"error": str(exception)}
        finally:
            self.pipeline.destroy_stream(stream_id)
            while not self.queue.empty():
                response = self.queue.get()
        return response
    
    def call_as_tool(self, tool_name: str, **kwargs) -> Dict[str, Any]:
        """ Call a tool in the pipeline

        :param tool_name: The name of the tool to call
        :type tool_name: str
        :param kwargs: The arguments to pass to the tool
        :type kwargs: Dict[str, Any]
        :return: The response from the tool
        :rtype: Dict[str, Any]
        """
        # paraneters and args are keys in the kwargs
        parameters = {}
        args = kwargs
        stream_id = kwargs.get("stream_id", DEFAULT_STREAM_ID)
        logger.info(f"Calling tool: {tool_name} with args: {args}")
        return self.invoke_graph(stream_id=stream_id,
                                 start_node=tool_name,
                                 parameters=parameters,
                                 kwargs=args)
    
    @staticmethod
    def create_function_with_explicit_kwargs(name, arg_dict, existing_function):
        """Creates a function with an explicit signature but calls the existing function using **kwargs."""
        
        # convert the list of dicts into a single dict
        arg_dict = {i['name']: i['default'] if 'default' in i else None for i in arg_dict}

        args = list(arg_dict.keys())

        @functools.wraps(existing_function)
        def wrapper(*func_args, **func_kwargs):
            bound_args = dict(zip(args, func_args))  # Map positional args
            bound_args.update(func_kwargs)  # Include keyword args
            return existing_function(**bound_args)  # Call original function

        # Define the new signature with explicit arguments
        parameters = [
            inspect.Parameter(arg, inspect.Parameter.POSITIONAL_OR_KEYWORD, default=default)
            for arg, default in arg_dict.items()
        ]
        
        wrapper.__signature__ = inspect.Signature(parameters)
        wrapper.__name__ = name  # Rename the function

        return wrapper

                                 

    
class AikoMCPServer(FastMCP):
    def __init__(self, pipeline_definition_pathname: str):
        self.assistant = Assistant(self, pipeline_definition_pathname)
        super().__init__("aiko_pipeline_server")

        # read the pipeline definition and identify any tools defined
        # Tools are defined by including a parameter called "tool" in the pipeline definition
        pipeline_defn = aiko.PipelineImpl.parse_pipeline_definition(self.assistant.pipeline_definition_pathname)
        print(f"pipelinedefn: {pipeline_defn}")
        self.tools = {}
        for node in pipeline_defn.elements:
            if "tool" in node.parameters.keys():
                tool_name = node.name
                self.tools[tool_name] = node
                tool_args = node.input
                # add the tool name to the args
                tool_args.append({"name": "tool_name", "default": tool_name})
                call_tool_fn = self.assistant.call_as_tool
                print(f"Adding tool: {tool_name}")
                self.add_tool(Assistant.create_function_with_explicit_kwargs(tool_name, tool_args, call_tool_fn,), 
                              name=tool_name, 
                              description=node.parameters["description"])

    def terminate(self):
        self.assistant.terminate()
        super().terminate()

    
    async def call_tool(
        self, name: str, arguments: dict
    ) -> Any:
        """Call a tool by name with arguments."""
        logger.info(f"Calling tool: {name} with arguments: {arguments}")
        return await super().call_tool(name, arguments)



def register_signal_handler(mcp_server):
    """ This is needed to clean up an running processes when the server is terminated 
    the MCP server doesn't give us any lifecycle hooks to do this so we have to use a signal handler    
    """
    def signal_handler(sig, frame):
        mcp_server.terminate()
        sys.exit(0)

    return signal_handler

if __name__ == "__main__":

    # Create the MCP server
    mcp_server = AikoMCPServer(os.path.join(PIPELINE_DEFINITION_FILES, PIPELINE_DEFINITION))

    # Register signal handler
    signal_handler = register_signal_handler(mcp_server)
    signal.signal(signal.SIGINT, signal_handler)

    mcp_server.run()