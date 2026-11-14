import os

from dotenv import load_dotenv
from livekit import agents
from livekit.agents import Agent, AgentServer, AgentSession, inference

load_dotenv(".env.local")


class PurnimaVoiceAssistant(Agent):
    def __init__(self) -> None:
        super().__init__(
            instructions=(
                "You are Purnima, a helpful voice assistant. "
                "Understand and respond naturally in Bengali when the user speaks Bengali. "
                "Follow the user's language when possible. Be concise, respectful, and clear. "
                "Do not claim to have performed actions unless a connected tool confirms success."
            )
        )


server = AgentServer()


@server.rtc_session(agent_name="purnima-voice")
async def purnima_voice(ctx: agents.JobContext) -> None:
    stt_model = os.environ["PUR_NIMA_STT_MODEL"]
    llm_model = os.environ["PUR_NIMA_LLM_MODEL"]
    tts_model = os.environ["PUR_NIMA_TTS_MODEL"]

    session = AgentSession(
        stt=inference.STT(model=stt_model),
        llm=inference.LLM(model=llm_model),
        tts=inference.TTS(model=tts_model),
    )

    await session.start(
        room=ctx.room,
        agent=PurnimaVoiceAssistant(),
    )
    await session.generate_reply(
        instructions="Greet the user briefly in Bengali and ask how you can help."
    )


if __name__ == "__main__":
    agents.cli.run_app(server)
