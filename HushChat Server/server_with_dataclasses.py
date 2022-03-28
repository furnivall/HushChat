from aiohttp import web
import socketio
from dataclasses import dataclass

# init server bits
sio = socketio.AsyncServer()
app = web.Application()
sio.attach(app)

# init empty containers
currently_connected_clients = []
usernames = {}
uids = {}

# dataclass representing a user and their attributes
@dataclass
class User:
    # public key
    pub_key: str
    # device unique identifier
    uid: str
    # chosen username
    username: str
    # socket.io unique id
    sid: str
    # connection status
    connected: bool


# dataclass representing user list
@dataclass
class UserList:
    users: list

    # function for getting dictionary of user ids/sids
    def get_user_ids(self):
        return {i.uid: i.sid for i in self.users}

    # function for getting dictionary of usernames/sids
    def get_usernames(self):
        return {i.sid: i.username for i in self.users}

    # function for getting a specific user object by searching for a corresponding sid
    def get_user_by_sid(self, searched_sid):
        return [x for x in self.users if x.sid == searched_sid][0]

    # get user object by device id - same method as above
    def get_user_by_uid(self, searched_uid):
        return [x for x in self.users if x.uid == searched_uid][0]

    # same as above for username
    def get_user_by_username(self, searched_username):
        return [x for x in self.users if x.username == searched_username][0]

    # returns a list of connected users
    def get_connected_users(self):
        return [i for i in self.users if i.connected]


existing_users = UserList([])

# connection event - initial handling when a client connects
@sio.event
async def connect(sid, environment):
    # create new user object associated with this sid and given placeholder name of UNREGISTERED USER.
    existing_users.users.append(User("", "", "UNREGISTERED_USER", sid, True))
    print("connect ", sid)
    print(f'Connected clients: {existing_users.get_connected_users()}')

    # build string to tell clients about other connected users.
    def if_empty(data):
        if len(data) == 0:
            return "No other clients are connected"
        else:
            d = {i: data[i] for i in data if i != sid}
            return f"Other connected clients are: {d}"

    # send welcome messages including data of other users.
    await sio.emit('init_message', f'Welcome to the server.\n\n', room=sid)
    await sio.emit('init_message', f"Your ID is {sid}\n\n", room=sid)
    await sio.emit('init_message', f'{if_empty(existing_users.get_usernames())}\n', room=sid)


# debug method to check if client messages are received.
@sio.event
async def message(sid, data):
    print(sid + " says " + data)

# replaces a client's public key with a shiny new one. This happens when user restarts the app.
@sio.event
async def update_pub_key(sid, key):
    existing_users.get_user_by_sid(sid).pub_key = key
    print(f'{existing_users.get_usernames().get(sid)} just sent their public key: {key}')

# handles registration of usernames for a new client.
@sio.event
async def newUsername(sid, username_string):
    existing_users.get_user_by_sid(sid).username = username_string.strip()
    await sio.emit('username_message', f"You have registered the new Username {username_string}\n", room=sid)
    print(f'{len(existing_users.users)} Users connected: {existing_users.users}')
    await sio.emit('username_message', f"New user connected.")
    await sio.emit('new_user', existing_users.get_user_by_sid(sid).username, skip_sid=sid)
    await sio.emit('username_message', f"Currently connected users : {list(existing_users.get_usernames().values())}\n")

    await sio.emit('user_reg', f"", room=sid)

# handles sending a chat message to a user from another user.
@sio.event
async def send_to_user(sid, payload):
    print(payload)
    print(f"{existing_users.get_usernames().get(sid)} says: {payload[1]}")
    key = next(key for key, value in existing_users.get_usernames().items() if value == payload[0])
    print(f"{existing_users.get_usernames().get(sid)} is attempting to say {payload[1]} to {payload[0]}")
    await sio.emit('chatmessage', {'message': payload[1], 'sender': f"{existing_users.get_usernames().get(sid)}",
                                   "pubkey":existing_users.get_user_by_sid(sid).pub_key.decode('utf-8')},
                   room=key)

# Request the public key of a particular user
@sio.event
async def getPubKey(sid, user):
    print(user)
    print(f'{existing_users.get_user_by_sid(sid).username} is requesting the public key for {user}')
    pubKey = existing_users.get_user_by_username(user).pub_key
    print(f'Emitting public key for user {user}: {pubKey}')
    await sio.emit('pubKeyResponse', pubKey, room=sid, callback=print("sent Callback"))

# Get list of users
@sio.event
async def getUsers(sid, data):
    usernameWithoutSid = [x for x in existing_users.get_usernames().values() if
                          x != existing_users.get_usernames().get(sid)]
    await sio.emit('users', usernameWithoutSid, room=sid)

# Initial sending of public key to the server from a client. Occurs when client starts up the app.
@sio.event
async def pubKey(sid, pubKey):
    print(f'{existing_users.get_usernames().get(sid)} just sent their public key: {pubKey}')
    existing_users.get_user_by_sid(sid).pub_key = pubKey

# handler for receiving the unique identifier of a device from a client
@sio.event
async def uid(sid, uid_string):
    print(f'here is a unique identifier for {sid}: {uid_string}')
    if uid_string in existing_users.get_user_ids():
        prev_user = existing_users.get_user_by_uid(uid_string)
        prev_username = prev_user.username
        prev_sid = prev_user.sid
        print(
            f'This uid ({uid_string}) has previously connected with the sid: '
            f'{existing_users.get_user_ids().get(uid_string)}. ')
        # if previously connected user has username:
        print(f'Existing username = {prev_username}. Adding reference to current sid and deleting dupe.')
        existing_users.get_user_by_sid(sid).username = prev_username
        # check that sid has been updated
        print(existing_users.get_user_by_sid(sid))
        # Deleting the former sid/username reference
        print(existing_users.users)
        existing_users.users.remove(existing_users.get_user_by_sid(prev_sid))
        existing_users.get_user_by_sid(sid).uid = uid_string
        await sio.emit('existingUserCheck', 'true', room=sid)
    else:
        existing_users.get_user_by_sid(sid).uid = uid_string
        print(f'Here are all uids for debugging purposes: {existing_users.get_user_ids()}')
        print(f'This uid ({uid_string} has never connected before, triggering username get from client')
        await sio.emit('existingUserCheck', 'false', room=sid)
    print(existing_users.users)

# handle disconnection events
@sio.event
def disconnect(sid):
    existing_users.get_user_by_sid(sid).connected = False
    print('disconnect ', sid)
    print(f'remaining clients: {existing_users.get_usernames()}')

# init code
if __name__ == '__main__':
    web.run_app(app)