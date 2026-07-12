package com.e_commerce.userservice.Interfaces;


import com.e_commerce.userservice.Dto.CreateUserDTO;
import com.e_commerce.userservice.Dto.UserDTO;

public interface IUserInterface {

   UserDTO findUserByUserName (String userName);

   UserDTO saveUser(CreateUserDTO createUserDTO);

   UserDTO updateUser(UserDTO userDTO);

   void deleteUser(Long userId);
}
