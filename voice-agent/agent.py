import os

from dotenv import load_dotenv
from livekit import agents
from livekit.agents import Agent, AgentServer, AgentSession, STTContextOptions, TurnHandlingOptions, inference
from livekit.plugins import gnani

load_dotenv()

server = AgentServer()


@server.rtc_session(agent_name="purnima-voice-agent")
async def purnima_voice_agent(ctx: agents.JobContext):
    session = AgentSession(
        stt=gnani.STT(
            language="bn-IN",
            use_streaming=True,
        ),
        llm=inference.LLM(
            model=os.getenv("PURNIMA_LLM_MODEL", "google/gemma-4-31b-it"),
        ),
        tts=gnani.TTS(
            voice=os.getenv("GNANI_TTS_VOICE", "Kaveri"),
            model="timbre-v2.5",
            language="bn-IN",
            synthesize_method="websocket",
        ),
        turn_handling=TurnHandlingOptions(
            turn_detection=inference.TurnDetector(),
        ),
        stt_context_options=STTContextOptions(
            keyterms=["Purnima", "পূর্ণিমা", "Mahiru", "মাহিরু"],
        ),
    )

    await session.start(
        room=ctx.room,
        agent=Agent(
            instructions=(
                "তোমার নাম পূর্ণিমা। তুমি একজন উষ্ণ, স্বাভাবিক, সহানুভূতিশীল বাংলা AI সঙ্গী। "
                "ব্যবহারকারী বাংলায় কথা বললে বাংলায় উত্তর দেবে; ইংরেজিতে বললে ইংরেজিতে উত্তর দেবে। "
                "কথা বলার সময় সংক্ষিপ্ত, স্বাভাবিক এবং বন্ধুত্বপূর্ণ বাক্য ব্যবহার করবে। "
                "নিজেকে মানুষ বলে পরিচয় দেবে না। ব্যবহারকারীর কথা মনোযোগ দিয়ে শুনবে, "
                "প্রয়োজন হলে একটি পরিষ্কার প্রশ্ন করবে এবং অনুমান করে ব্যক্তিগত তথ্য বানাবে না।"
            ),
        ),
    )
    await session.generate_reply(
        instructions="ব্যবহারকারীকে বাংলায় সংক্ষেপে স্বাগত জানাও এবং কীভাবে সাহায্য করতে পারো জিজ্ঞাসা করো।"
    )


if __name__ == "__main__":
    agents.cli.run_app(server)
