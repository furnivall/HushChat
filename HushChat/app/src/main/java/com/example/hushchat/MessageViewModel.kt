package com.example.hushchat

import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.lang.IllegalArgumentException

class MessageViewModel(private val repository: MessageRepository) : ViewModel() {
//    val allMessages: LiveData<List<ChatMessage>> = repository.allMessages.asLiveData()
    fun relevantMessages(sender:String): LiveData<List<ChatMessage>> {
        return repository.getRelevantMessages(sender).asLiveData()
    }
//    val allMessages: LiveData<List<ChatMessage>> = repository.getRelevantMessages().asLiveData()

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