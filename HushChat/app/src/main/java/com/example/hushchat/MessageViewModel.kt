package com.example.hushchat

import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.lang.IllegalArgumentException

class MessageViewModel(private val repository: MessageRepository) : ViewModel() {
    val allMessages: LiveData<List<ChatMessage>> = repository.allMessages.asLiveData()
//    val relevantMessages: LiveData<List<ChatMessage>> = repository.relevantMessages.asLiveData()

    fun insert(message: ChatMessage) = viewModelScope.launch {
        repository.insert(message)
    }
}

class MessageViewModelFactory(private val repository: MessageRepository) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MessageViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel Class")
    }


}