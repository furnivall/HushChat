package com.example.hushchat

import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.lang.IllegalArgumentException

/**
 * Viewmodel class which calls our repository methods (which then call the relevant DAO object)
 */
class MessageViewModel(private val repository: MessageRepository) : ViewModel() {

    /**
     * Gets relevant messages for target chat window. The .asLiveData allows the data to be updated
     * upon new messages.
     */
    fun relevantMessages(sender:String): LiveData<List<ChatMessage>> {
        return repository.getRelevantMessages(sender).asLiveData()
    }

    /**
     * Self destruct method informed by duration
     */
    fun deleteAccordingToTimeFrame(dateTime: Long) {
        repository.deleteAccordingToTimeFrame(dateTime)
    }

    /**
     * Insert method for new messages on receipt
     */
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