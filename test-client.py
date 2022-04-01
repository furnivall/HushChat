import asyncio
import socketio
import sys

sio = socketio.AsyncClient()


async def ainput(string: str) -> str:
    await asyncio.get_event_loop().run_in_executor(
        None, lambda s=string: sys.stdout.write(s + ' '))
    return await asyncio.get_event_loop().run_in_executor(
        None, sys.stdin.readline)

@sio.event
async def user_reg(data):
    for i in range(1000):
        recipient = (await ainput("Who do you want to send a message to?"))[:-1]
        text = (await ainput("what do you want to say?"))[:-1]
        if recipient == "exit" or text == "exit":
            await sio.emit("disconnect")
            exit()
        else:
            await sio.emit('send_to_user', [recipient, text])

@sio.event
async def connect():
    print('connection established')
    response = await ainput("what is your newUsername")
    await sio.emit('newUsername', response)


@sio.event
async def init_message(data):
    print(data)


@sio.event
async def chatmessage(data):
    print(data)

@sio.event
async def username_message(data):
    print(data)

@sio.event
async def disconnect():
    print('disconnected from server')

async def main():
    await sio.connect('http://20.126.74.125:8080')
    await sio.wait()


if __name__ == '__main__':
    asyncio.run(main())
